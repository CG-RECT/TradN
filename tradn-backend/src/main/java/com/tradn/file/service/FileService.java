package com.tradn.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tradn.common.exception.BizException;
import com.tradn.config.MinioConfig;
import com.tradn.file.mapper.FileObjectMapper;
import com.tradn.file.model.FileObject;
import com.tradn.security.SecurityUtils;
import io.minio.*;
import io.minio.http.Method;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileService {
    private static final Set<String> ALLOWED =
            new HashSet<String>(
                    Arrays.asList("image/jpeg", "image/png", "image/webp", "image/gif"));
    private final MinioClient minio;
    private final MinioConfig config;
    private final FileObjectMapper mapper;
    private final JdbcTemplate jdbc;

    public FileService(
            MinioClient minio, MinioConfig config, FileObjectMapper mapper, JdbcTemplate jdbc) {
        this.minio = minio;
        this.config = config;
        this.mapper = mapper;
        this.jdbc = jdbc;
    }

    // MinIO 与 MySQL 不共享事务：数据库失败时对象可能暂时孤立；当前定时任务只能处理已有元数据，
    // 未落库对象需要后续通过 MinIO 对象清单与数据库 object_key 对账清理。
    @Transactional
    public FileObject upload(
            MultipartFile file, String businessType, long businessId, String usageType) {
        if (file.isEmpty() || !ALLOWED.contains(file.getContentType()))
            throw new BizException("只允许上传 JPG、PNG、WebP 或 GIF 图片");
        try {
            byte[] bytes = file.getBytes();
            ensureBucket();
            String ext = extension(file.getOriginalFilename(), file.getContentType());
            // 对象键包含用户与日期目录但不包含原始文件名，避免路径穿越、重名和敏感名称泄露。
            String prefix =
                    SecurityUtils.userId()
                            + "/"
                            + LocalDate.now().toString().replace("-", "/")
                            + "/"
                            + UUID.randomUUID().toString().replace("-", "");
            String objectKey = "original/" + prefix + ext;
            String thumbKey = "thumbnail/" + prefix + ".jpg";
            minio.putObject(
                    PutObjectArgs.builder().bucket(config.getBucket()).object(objectKey).stream(
                                    new ByteArrayInputStream(bytes), bytes.length, -1)
                            .contentType(file.getContentType())
                            .build());
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            byte[] thumb = null;
            if (image != null) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                Thumbnails.of(image)
                        .size(360, 240)
                        .outputFormat("jpg")
                        .outputQuality(0.82)
                        .toOutputStream(out);
                thumb = out.toByteArray();
                minio.putObject(
                        PutObjectArgs.builder().bucket(config.getBucket()).object(thumbKey).stream(
                                        new ByteArrayInputStream(thumb), thumb.length, -1)
                                .contentType("image/jpeg")
                                .build());
            }
            FileObject obj = new FileObject();
            obj.setUserId(SecurityUtils.userId());
            obj.setBucketName(config.getBucket());
            obj.setObjectKey(objectKey);
            obj.setThumbnailObjectKey(thumb == null ? null : thumbKey);
            obj.setOriginalName(safeName(file.getOriginalFilename()));
            obj.setMimeType(file.getContentType());
            obj.setFileSize((long) bytes.length);
            obj.setSha256(DigestUtils.sha256Hex(bytes));
            obj.setImageWidth(image == null ? null : image.getWidth());
            obj.setImageHeight(image == null ? null : image.getHeight());
            obj.setUploadStatus("READY");
            mapper.insert(obj);
            jdbc.update(
                    "INSERT INTO business_file_relation(file_id,business_type,business_id,usage_type,sort_no) VALUES(?,?,?,?,0)",
                    obj.getId(),
                    businessType,
                    businessId,
                    usageType == null ? "ATTACHMENT" : usageType);
            return obj;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException("文件上传失败：" + ex.getMessage());
        }
    }

    public List<Map<String, Object>> list(String businessType, long businessId) {
        List<Map<String, Object>> rows =
                jdbc.queryForList(
                        "SELECT f.id,f.original_name,f.mime_type,f.file_size,f.image_width,f.image_height,r.usage_type,r.sort_no FROM file_object f JOIN business_file_relation r ON r.file_id=f.id WHERE r.business_type=? AND r.business_id=? AND f.user_id=? AND f.deleted=0 ORDER BY r.sort_no,f.created_at",
                        businessType,
                        businessId,
                        SecurityUtils.userId());
        for (Map<String, Object> row : rows) {
            long id = ((Number) row.get("id")).longValue();
            row.put("thumbnailUrl", url(id, true));
            row.put("originalUrl", url(id, false));
        }
        return rows;
    }
    // 每次访问先校验文件所有权，再签发短时 URL；MinIO 桶保持私有，不向前端暴露永久地址。
    public String url(long id, boolean thumbnail) {
        FileObject obj =
                mapper.selectOne(
                        new LambdaQueryWrapper<FileObject>()
                                .eq(FileObject::getId, id)
                                .eq(FileObject::getUserId, SecurityUtils.userId()));
        if (obj == null) throw new BizException(404, "文件不存在");
        String key =
                thumbnail && obj.getThumbnailObjectKey() != null
                        ? obj.getThumbnailObjectKey()
                        : obj.getObjectKey();
        try {
            return minio.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(obj.getBucketName())
                            .object(key)
                            .method(Method.GET)
                            .expiry(15, TimeUnit.MINUTES)
                            .build());
        } catch (Exception ex) {
            throw new BizException("生成访问地址失败");
        }
    }

    @Transactional
    public void removeRelation(long fileId, String businessType, long businessId) {
        FileObject obj =
                mapper.selectOne(
                        new LambdaQueryWrapper<FileObject>()
                                .eq(FileObject::getId, fileId)
                                .eq(FileObject::getUserId, SecurityUtils.userId()));
        if (obj == null) throw new BizException(404, "文件不存在");
        jdbc.update(
                "DELETE FROM business_file_relation WHERE file_id=? AND business_type=? AND business_id=?",
                fileId,
                businessType,
                businessId);
    }

    private void ensureBucket() throws Exception {
        if (!minio.bucketExists(BucketExistsArgs.builder().bucket(config.getBucket()).build()))
            minio.makeBucket(MakeBucketArgs.builder().bucket(config.getBucket()).build());
    }

    private String safeName(String name) {
        if (name == null) return "image";
        String n = name.replace("\\", "/");
        n = n.substring(n.lastIndexOf('/') + 1);
        return n.substring(0, Math.min(n.length(), 250));
    }

    private String extension(String name, String mime) {
        if (name != null && name.lastIndexOf('.') >= 0) {
            String e = name.substring(name.lastIndexOf('.')).toLowerCase();
            if (e.matches("\\.(jpg|jpeg|png|webp|gif)")) return e;
        }
        return "image/png".equals(mime)
                ? ".png"
                : "image/webp".equals(mime) ? ".webp" : "image/gif".equals(mime) ? ".gif" : ".jpg";
    }
}

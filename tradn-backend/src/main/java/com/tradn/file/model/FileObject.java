package com.tradn.file.model;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tradn.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("file_object")
/** MinIO 文件对象元数据，不保存文件二进制内容。 */
public class FileObject extends BaseEntity {
    /** 文件所属账号 ID，用于数据隔离。 */
    private Long userId;

    /** MinIO 存储桶名称。 */
    private String bucketName;

    /** 原始文件在 MinIO 中的对象键。 */
    private String objectKey;

    /** 图片缩略图在 MinIO 中的对象键，非图片可为空。 */
    private String thumbnailObjectKey;

    /** 用户上传时的原始文件名。 */
    private String originalName;

    /** 文件 MIME 类型。 */
    private String mimeType;

    /** 文件大小，单位为字节。 */
    private Long fileSize;

    /** 文件内容的 SHA-256 摘要，用于识别重复文件。 */
    private String sha256;

    /** 原始图片宽度，单位为像素。 */
    private Integer imageWidth;

    /** 原始图片高度，单位为像素。 */
    private Integer imageHeight;

    /** 上传状态，用于区分上传中、可用或失败文件。 */
    private String uploadStatus;
}

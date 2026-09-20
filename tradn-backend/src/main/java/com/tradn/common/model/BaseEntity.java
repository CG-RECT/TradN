package com.tradn.common.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.LocalDateTime;
import lombok.Data;

@Data
/** 所有业务实体共享的主键、审计、逻辑删除和乐观锁字段。 */
public abstract class BaseEntity {
    /** 分布式唯一主键。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 创建人账号 ID，由 MyBatis-Plus 自动填充。 */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /** 创建时间，由 MyBatis-Plus 自动填充。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 最后修改人账号 ID，由 MyBatis-Plus 自动填充。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    /** 最后修改时间，由 MyBatis-Plus 自动填充。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除标识：0 表示有效，1 表示已删除。 */
    @TableLogic private Integer deleted;

    /** 乐观锁版本号，更新时用于避免并发覆盖。 */
    @Version private Integer version;
}

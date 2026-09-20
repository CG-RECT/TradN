package com.tradn.system.model;

import lombok.Data;

/** Redis 缓存命名空间新增或修改请求。 */
@Data
public class CacheNamespaceCommand {
    /** 缓存命名空间编码。 */
    private String namespace;

    /** 缓存命名空间显示名称。 */
    private String displayName;

    /** 缓存用途说明。 */
    private String description;

    /** 状态：ENABLED启用、DISABLED停用。 */
    private String status;
}

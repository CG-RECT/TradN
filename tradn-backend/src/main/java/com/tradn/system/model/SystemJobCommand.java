package com.tradn.system.model;

import lombok.Data;

/** 系统调度任务新增或修改请求。 */
@Data
public class SystemJobCommand {
    /** 任务唯一编码，对应服务端已注册的任务实现。 */
    private String jobCode;

    /** 任务显示名称。 */
    private String jobName;

    /** Quartz Cron 表达式。 */
    private String cronExpression;

    /** 调度时区。 */
    private String timezone;

    /** 任务状态：ENABLED启用、PAUSED暂停。 */
    private String status;

    /** 任务用途说明。 */
    private String description;
}

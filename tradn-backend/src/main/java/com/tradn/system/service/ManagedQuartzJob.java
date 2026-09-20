package com.tradn.system.service;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

@Component
@DisallowConcurrentExecution
public class ManagedQuartzJob implements Job {
    private final MaintenanceTaskService taskService;

    public ManagedQuartzJob(MaintenanceTaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public void execute(JobExecutionContext context) {
        taskService.execute(
                context.getMergedJobDataMap().getString("jobCode"),
                context.getMergedJobDataMap().getString("triggerType"));
    }
}

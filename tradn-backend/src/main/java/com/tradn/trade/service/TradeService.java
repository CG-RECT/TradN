package com.tradn.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tradn.common.exception.BizException;
import com.tradn.security.SecurityUtils;
import com.tradn.trade.mapper.TradeRecordMapper;
import com.tradn.trade.model.TradeCommand;
import com.tradn.trade.model.TradeRecord;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TradeService {
    private final TradeRecordMapper mapper;
    private final JdbcTemplate jdbc;

    public TradeService(TradeRecordMapper mapper, JdbcTemplate jdbc) {
        this.mapper = mapper;
        this.jdbc = jdbc;
    }

    // 交易属于用户私有数据，所有列表和详情入口都必须从查询条件上隔离当前用户。
    public Page<TradeRecord> list(int page, int size, String status) {
        LambdaQueryWrapper<TradeRecord> q =
                new LambdaQueryWrapper<TradeRecord>()
                        .eq(TradeRecord::getUserId, SecurityUtils.userId())
                        .eq(status != null && !status.isEmpty(), TradeRecord::getStatus, status)
                        .orderByDesc(TradeRecord::getCreatedAt);
        return mapper.selectPage(new Page<TradeRecord>(page, Math.min(size, 100)), q);
    }

    public Map<String, Object> detail(long id) {
        TradeRecord trade = owned(id);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("trade", trade);
        result.put(
                "questions",
                jdbc.queryForList(
                        "SELECT * FROM questionnaire_question WHERE template_id=? ORDER BY sort_no",
                        trade.getQuestionnaireTemplateId()));
        result.put(
                "answers",
                jdbc.queryForList("SELECT * FROM trade_questionnaire_answer WHERE trade_id=?", id));
        return result;
    }

    /** 查询新建页面使用的问卷模板，不创建任何交易记录。 */
    public Map<String, Object> questionnaireTemplate() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put(
                "template",
                jdbc.queryForMap(
                        "SELECT * FROM questionnaire_template WHERE id=1 AND status='ENABLED'"));
        result.put(
                "questions",
                jdbc.queryForList(
                        "SELECT * FROM questionnaire_question WHERE template_id=1 ORDER BY sort_no"));
        return result;
    }

    // 只有用户点击保存且问卷校验通过时才创建记录，事务失败不会残留草稿数据。
    @Transactional
    public TradeRecord create(TradeCommand c) {
        validatePlan(c);
        TradeRecord t = new TradeRecord();
        t.setUserId(SecurityUtils.userId());
        t.setRecordNo(
                "TR"
                        + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                        + String.format("%03d", new Random().nextInt(1000)));
        t.setStatus("PLANNED");
        t.setQuestionnaireTemplateId(1L);
        copyPlan(t, c);
        mapper.insert(t);
        saveAnswers(t.getId(), c.getAnswers(), "BEFORE_OPEN", true);
        return t;
    }
    // 问卷答案和交易状态在同一事务内保存；version 冲突时整体回滚，避免半份计划。
    @Transactional
    public void savePlan(long id, TradeCommand c) {
        TradeRecord t = owned(id);
        copyPlan(t, c);
        validatePlan(c);
        saveAnswers(id, c.getAnswers(), "BEFORE_OPEN", true);
        t.setStatus("PLANNED");
        t.setVersion(c.getVersion());
        if (mapper.updateById(t) == 0) throw new BizException(409, "记录已被其他页面修改，请刷新");
    }

    @Transactional
    public void open(long id, TradeCommand c) {
        TradeRecord t = owned(id);
        TradeStatusPolicy.requireCanOpen(t.getStatus());
        if (c.getOpenPrice() == null || c.getLotSize() == null || c.getOpenTime() == null)
            throw new BizException("开仓价、手数和开仓时间必须填写");
        t.setOpenPrice(c.getOpenPrice());
        t.setLotSize(c.getLotSize());
        t.setOpenTime(c.getOpenTime());
        t.setStatus("OPEN");
        t.setVersion(c.getVersion());
        if (mapper.updateById(t) == 0) throw new BizException(409, "记录已被修改");
    }

    @Transactional
    public void review(long id, TradeCommand c) {
        TradeRecord t = owned(id);
        TradeStatusPolicy.requireCanReview(t.getStatus());
        saveAnswers(id, c.getAnswers(), "AFTER_OPEN", false);
    }

    @Transactional
    public void close(long id, TradeCommand c) {
        TradeRecord t = owned(id);
        TradeStatusPolicy.requireCanClose(t.getStatus());
        if (c.getCloseTime() == null || c.getProfitLoss() == null)
            throw new BizException("平仓时间和实际盈亏必须填写");
        t.setClosePrice(c.getClosePrice());
        t.setCloseTime(c.getCloseTime());
        t.setProfitLoss(c.getProfitLoss());
        t.setStatus("CLOSED");
        t.setVersion(c.getVersion());
        if (mapper.updateById(t) == 0) throw new BizException(409, "记录已被修改");
    }
    // 将结束日期转换为次日零点，按 [from, to) 统计，保证用户选择的结束日完整计入。
    public Map<String, Object> statistics(LocalDate from, LocalDate to) {
        Map<String, Object> value =
                mapper.statistics(
                        SecurityUtils.userId(), from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        long wins = number(value.get("win_count")), losses = number(value.get("loss_count"));
        value.put(
                "winRate",
                wins + losses == 0
                        ? BigDecimal.ZERO
                        : BigDecimal.valueOf(wins * 100.0 / (wins + losses))
                                .setScale(2, BigDecimal.ROUND_HALF_UP));
        return value;
    }

    private void copyPlan(TradeRecord t, TradeCommand c) {
        t.setDirection(c.getDirection());
        t.setPlannedTakeProfitPrice(c.getPlannedTakeProfitPrice());
        t.setPlannedStopLossPrice(c.getPlannedStopLossPrice());
        t.setRemark(c.getRemark());
    }

    private void validatePlan(TradeCommand command) {
        if (command.getDirection() == null
                || command.getPlannedTakeProfitPrice() == null
                || command.getPlannedStopLossPrice() == null) {
            throw new BizException("方向、计划止盈和计划止损必须填写");
        }
    }
    // 将“记录不存在”和“无权访问”统一为 404，避免泄露其他用户的记录 ID。
    private TradeRecord owned(long id) {
        TradeRecord t =
                mapper.selectOne(
                        new LambdaQueryWrapper<TradeRecord>()
                                .eq(TradeRecord::getId, id)
                                .eq(TradeRecord::getUserId, SecurityUtils.userId()));
        if (t == null) throw new BizException(404, "开仓记录不存在");
        return t;
    }

    private void saveAnswers(
            long tradeId, Map<Long, Object> answers, String phase, boolean validate) {
        if (answers == null) answers = Collections.emptyMap();
        List<Map<String, Object>> questions =
                jdbc.queryForList(
                        "SELECT id,question_type,required_flag FROM questionnaire_question WHERE template_id=1 AND phase=?",
                        phase);
        // 按题目类型落入不同答案列，并利用唯一键 UPSERT，重复保存不会产生多条答案。
        for (Map<String, Object> q : questions) {
            Long qid = ((Number) q.get("id")).longValue();
            Object answer = answers.get(qid);
            if (validate
                    && number(q.get("required_flag")) == 1
                    && (answer == null || String.valueOf(answer).trim().isEmpty()))
                throw new BizException("必填问卷项未完成：" + qid);
            if (answer == null) continue;
            String type = String.valueOf(q.get("question_type"));
            Boolean bool = "BOOLEAN".equals(type) ? Boolean.valueOf(String.valueOf(answer)) : null;
            BigDecimal num = "NUMBER".equals(type) ? new BigDecimal(String.valueOf(answer)) : null;
            String text = (bool == null && num == null) ? String.valueOf(answer) : null;
            jdbc.update(
                    "INSERT INTO trade_questionnaire_answer(id,trade_id,question_id,boolean_answer,number_answer,text_answer,updated_at) VALUES(?,?,?,?,?,?,NOW(3)) ON DUPLICATE KEY UPDATE boolean_answer=VALUES(boolean_answer),number_answer=VALUES(number_answer),text_answer=VALUES(text_answer),updated_at=NOW(3)",
                    com.baomidou.mybatisplus.core.toolkit.IdWorker.getId(),
                    tradeId,
                    qid,
                    bool,
                    num,
                    text);
        }
    }

    private long number(Object v) {
        return v == null ? 0 : ((Number) v).longValue();
    }
}

package edu.cga.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import edu.cga.common.BizException;
import edu.cga.common.IdGenerator;
import edu.cga.common.TimeService;
import edu.cga.domain.entity.Department;
import edu.cga.domain.entity.JobTemplate;
import edu.cga.domain.mapper.DepartmentMapper;
import edu.cga.domain.mapper.JobTemplateMapper;
import edu.cga.scope.DataScope;
import edu.cga.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 岗位模板（P1 设计 1.2）：本人可见全校通用 + 本院系；导师、院系管理员存本院系，校级管理员可存全校通用；
 * 创建者或校级管理员可删除。
 */
@Service
public class JobTemplateService {

    /** payload 序列化后的长度上限，防止异常大的请求写进数据库 */
    private static final int MAX_PAYLOAD_LENGTH = 20_000;

    /** 模板只保存表单字段，不保存岗位名称和由系统生成的字段 */
    private static final List<String> STRIPPED_KEYS = List.of("title", "id", "operator", "mentorId", "status");

    private final JobTemplateMapper templateMapper;
    private final DepartmentMapper departmentMapper;
    private final DataScope scope;
    private final AuditService audit;
    private final IdGenerator ids;
    private final TimeService time;
    private final JsonMapper jsonMapper;

    public JobTemplateService(JobTemplateMapper templateMapper, DepartmentMapper departmentMapper, DataScope scope,
                              AuditService audit, IdGenerator ids, TimeService time, JsonMapper jsonMapper) {
        this.templateMapper = templateMapper;
        this.departmentMapper = departmentMapper;
        this.scope = scope;
        this.audit = audit;
        this.ids = ids;
        this.time = time;
        this.jsonMapper = jsonMapper;
    }

    /** 本人可见的模板：全校通用在前（MySQL 升序时 NULL 排最前），其余按创建时间倒序 */
    public List<JobTemplate> list(CurrentUser u) {
        return templateMapper.selectList(scope.jobTemplates(u).applyTo(new QueryWrapper<JobTemplate>())
                .orderByAsc("dept_id").orderByDesc("created_at").orderByAsc("id"));
    }

    /**
     * 存为模板。global=true 表示全校通用，仅校级管理员可用；校级管理员不选全校通用时可指定院系（默认本人院系）。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JobTemplate create(String name, String type, boolean global, String deptId, Map<String, Object> payload,
                              CurrentUser operator) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > 64) {
            throw BizException.badRequest("模板名称不能为空且不超过 64 字");
        }
        if (type == null || !ConfigService.JOB_TYPES.contains(type)) {
            throw BizException.badRequest("岗位类型必须为 TA、RA 或 GA");
        }
        if (payload == null) {
            throw BizException.badRequest("模板内容不能为空");
        }
        String targetDept;
        if (global) {
            if (!operator.is(CurrentUser.SCHOOL_ADMIN)) {
                throw BizException.forbidden("只有校级管理员可以保存全校通用模板");
            }
            targetDept = null;
        } else if (operator.is(CurrentUser.SCHOOL_ADMIN) && deptId != null && !deptId.isBlank()) {
            Department dept = departmentMapper.selectById(deptId);
            if (dept == null) {
                throw BizException.badRequest("院系不存在");
            }
            targetDept = deptId;
        } else {
            targetDept = operator.deptId();
        }

        Map<String, Object> clean = new LinkedHashMap<>(payload);
        STRIPPED_KEYS.forEach(clean::remove);
        clean.put("type", type);
        if (jsonMapper.writeValueAsString(clean).length() > MAX_PAYLOAD_LENGTH) {
            throw BizException.badRequest("模板内容过大");
        }

        JobTemplate t = new JobTemplate();
        t.setId(ids.next("JT"));
        t.setName(trimmed);
        t.setType(type);
        t.setDeptId(targetDept);
        t.setPayload(clean);
        t.setCreatedBy(operator.id());
        t.setCreatedAt(time.now());
        templateMapper.insert(t);

        audit.log("JOB_TEMPLATE", t.getId(), operator, "保存岗位模板",
                "保存 " + type + " 岗位模板「" + trimmed + "」（" + (targetDept == null ? "全校通用" : "院系 " + targetDept) + "）",
                "无", "已保存");
        return t;
    }

    /** 删除模板：不在可见范围 404；不是创建者且不是校级管理员 403 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JobTemplate delete(String id, CurrentUser operator) {
        JobTemplate t = scope.requireVisible(templateMapper, scope.jobTemplates(operator), id, "模板不存在");
        if (!operator.is(CurrentUser.SCHOOL_ADMIN) && !operator.id().equals(t.getCreatedBy())) {
            throw BizException.forbidden("只能删除自己创建的模板");
        }
        templateMapper.deleteById(id);
        audit.log("JOB_TEMPLATE", t.getId(), operator, "删除岗位模板", "删除 " + t.getType() + " 岗位模板「" + t.getName() + "」",
                "已保存", "已删除");
        return t;
    }
}

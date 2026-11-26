package edu.cga.integration.research;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * 科研系统对接接口（P2 B.3）。默认实现 {@link MockResearchGateway} 读 integration/research-projects.json；
 * 对接真实科研系统时新增一个实现类并通过 app.integration.research-provider 切换。
 */
public interface ResearchGateway {

    /**
     * 科研项目。
     *
     * @param code           项目编号（与 RA 岗位的 extraFields.projectCode 对应）
     * @param name           项目名称
     * @param leaderId       负责人（本系统用户 ID）
     * @param leaderName     负责人姓名
     * @param startDate      开始日期
     * @param endDate        结束日期
     * @param availableFunds 可用经费（元，仅展示）
     */
    record ResearchProject(String code, String name, String leaderId, String leaderName, LocalDate startDate,
                           LocalDate endDate, BigDecimal availableFunds) {
    }

    /** 按项目编号查询（编号去掉首尾空格后精确匹配，不区分大小写） */
    Optional<ResearchProject> findProject(String code);

    /** 数据来源名称（返回给前端展示） */
    String sourceName();
}

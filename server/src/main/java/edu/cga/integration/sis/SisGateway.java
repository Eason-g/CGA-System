package edu.cga.integration.sis;

import java.time.LocalDate;
import java.util.List;

/**
 * 学籍系统对接接口（P2 B.2）。默认实现 {@link MockSisGateway} 读 integration/sis-students.json；
 * 对接真实学籍系统时新增一个实现类并通过 app.integration.sis-provider 切换。
 */
public interface SisGateway {

    /**
     * 学籍系统中的一名学生。
     *
     * @param studentNo        学号（与本系统 users.student_no 匹配）
     * @param name             姓名
     * @param enrollmentStatus 学籍状态：在读 / 休学 / 毕业
     * @param visaType         签证类型（国际生）
     * @param visaExpiry       签证有效期（国际生）
     * @param languageCerts    已登记的语言证书（仅展示，语言证明以国际生办核验为准）
     */
    record SisStudent(String studentNo, String name, String enrollmentStatus, String visaType, LocalDate visaExpiry,
                      List<String> languageCerts) {
    }

    /** 拉取全部学生的学籍记录 */
    List<SisStudent> fetchStudents();

    /** 数据来源名称（返回给前端展示） */
    String sourceName();
}

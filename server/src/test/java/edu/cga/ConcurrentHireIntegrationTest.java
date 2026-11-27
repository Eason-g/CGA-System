package edu.cga;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import edu.cga.common.BizException;
import edu.cga.domain.entity.Contract;
import edu.cga.domain.entity.JobApplication;
import edu.cga.security.CurrentUser;
import edu.cga.service.ApplicationService;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 设计文档第 9 节第 6 条：两位导师同时录用同一学生到两个岗位（各自不超限、合计超限），最终只有一个成功。
 * <p>
 * 两个线程各自直接调用 ApplicationService.decide，每次调用都是 Service 自己的独立事务（测试类本身没有事务），
 * 用 CountDownLatch 让两边尽量同时进入。录用前 SELECT … FOR UPDATE 锁学生行，后到的一方等前者提交后
 * 再按最新合同累计工时，因此必然被拦截。重复 3 次以覆盖两种先后顺序。
 */
class ConcurrentHireIntegrationTest extends WriteApiTestBase {

    @Autowired
    private ApplicationService applicationService;

    private int round;

    @RepeatedTest(3)
    void onlyOneOfTwoConcurrentHiresSucceeds() throws Exception {
        round++;
        String stu = "T_CONC_" + round;
        createStudent(stu, "DEPT_CS", false, true);
        // 计算机学院张明远、数学学院陈守仁各发布一个 12h 岗位：单独录用都不超限，合计 24h > 20h
        createPublishedJob("T_JA_" + round, "U_MENTOR", "DEPT_CS", "B_CS_RA", 12, false);
        createPublishedJob("T_JB_" + round, "U_MENTOR_MATH", "DEPT_MATH", "B_MATH_TA", 12, false);
        JobApplication appA = createApplication("T_APA_" + round, "T_JA_" + round, stu, "SUBMITTED");
        JobApplication appB = createApplication("T_APB_" + round, "T_JB_" + round, stu, "SUBMITTED");

        CurrentUser mentorCs = CurrentUser.from(userMapper.selectById("U_MENTOR"));
        CurrentUser mentorMath = CurrentUser.from(userMapper.selectById("U_MENTOR_MATH"));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> results = new ArrayList<>();
            results.add(pool.submit(hire(appA.getId(), mentorCs, ready, go)));
            results.add(pool.submit(hire(appB.getId(), mentorMath, ready, go)));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            List<String> outcomes = new ArrayList<>();
            for (Future<String> f : results) {
                outcomes.add(f.get(30, TimeUnit.SECONDS));
            }
            assertThat(outcomes).containsExactlyInAnyOrder("OK", "403");
        } finally {
            pool.shutdownNow();
        }

        // 该生合同累计工时不超上限，两个岗位合计只录用了 1 人
        int allocated = contractMapper.selectAllocatingByStudent(stu).stream().mapToInt(Contract::getWeeklyHours).sum();
        assertThat(allocated).isEqualTo(12).isLessThanOrEqualTo(20);
        int hired = jobMapper.selectById("T_JA_" + round).getHiredCount() + jobMapper.selectById("T_JB_" + round).getHiredCount();
        assertThat(hired).isEqualTo(1);
        long hiredApps = List.of(appA.getId(), appB.getId()).stream()
                .filter(id -> "HIRED".equals(applicationMapper.selectById(id).getStatus())).count();
        assertThat(hiredApps).isEqualTo(1);
    }

    private Callable<String> hire(String appId, CurrentUser mentor, CountDownLatch ready, CountDownLatch go) {
        return () -> {
            ready.countDown();
            go.await();
            try {
                applicationService.decide(appId, 90, "HIRE", "并发录用测试", mentor);
                return "OK";
            } catch (BizException e) {
                return String.valueOf(e.getCode());
            }
        };
    }
}

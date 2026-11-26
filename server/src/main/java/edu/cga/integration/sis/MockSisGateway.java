package edu.cga.integration.sis;

import java.util.List;

import edu.cga.config.IntegrationProperties;
import edu.cga.integration.IntegrationDataLoader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 模拟学籍系统（演示用）：读 app.integration.sis-data 指向的 JSON（默认 integration/sis-students.json）。
 * 演示数据里有一条签证有效期变更、一条学籍状态变为休学，便于展示同步差异。
 */
@Component
@ConditionalOnProperty(name = "app.integration.sis-provider", havingValue = "mock", matchIfMissing = true)
public class MockSisGateway implements SisGateway {

    private final IntegrationProperties props;
    private final IntegrationDataLoader loader;

    public MockSisGateway(IntegrationProperties props, IntegrationDataLoader loader) {
        this.props = props;
        this.loader = loader;
    }

    @Override
    public List<SisStudent> fetchStudents() {
        return loader.readList(props.getIntegration().getSisData(), SisStudent.class);
    }

    @Override
    public String sourceName() {
        return "模拟学籍系统";
    }
}

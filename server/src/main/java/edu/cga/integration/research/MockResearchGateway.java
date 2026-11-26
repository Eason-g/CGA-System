package edu.cga.integration.research;

import java.util.Optional;

import edu.cga.config.IntegrationProperties;
import edu.cga.integration.IntegrationDataLoader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 模拟科研系统（演示用）：依次读 app.integration.research-data 列出的 JSON 文件
 * （默认 integration/research-projects.json；测试环境另加测试专用项目），同编号的项目后面的文件覆盖前面的。
 */
@Component
@ConditionalOnProperty(name = "app.integration.research-provider", havingValue = "mock", matchIfMissing = true)
public class MockResearchGateway implements ResearchGateway {

    private final IntegrationProperties props;
    private final IntegrationDataLoader loader;

    public MockResearchGateway(IntegrationProperties props, IntegrationDataLoader loader) {
        this.props = props;
        this.loader = loader;
    }

    @Override
    public Optional<ResearchProject> findProject(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String q = code.trim();
        ResearchProject found = null;
        for (String location : props.getIntegration().getResearchData()) {
            for (ResearchProject p : loader.readList(location, ResearchProject.class)) {
                if (p.code() != null && p.code().trim().equalsIgnoreCase(q)) {
                    found = p;
                }
            }
        }
        return Optional.ofNullable(found);
    }

    @Override
    public String sourceName() {
        return "模拟科研系统";
    }
}

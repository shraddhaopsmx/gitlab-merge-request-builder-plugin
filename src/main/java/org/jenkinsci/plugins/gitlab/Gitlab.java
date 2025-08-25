package org.jenkinsci.plugins.gitlab;

import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * GitLab4J API Wrapper Class
 */
public class Gitlab {
    private static final Logger _logger = Logger.getLogger(Gitlab.class.getName());
    private GitLabApi _api;

    private void connect() {
        String privateToken = GitlabBuildTrigger.getDesc().getBotApiToken();
        String apiUrl = GitlabBuildTrigger.getDesc().getGitlabHostUrl();
        try {
            _api = new GitLabApi(apiUrl, privateToken);
        } catch (Exception e) {
            _logger.log(Level.SEVERE, "Failed to connect to GitLab API at " + apiUrl, e);
        }
    }

    public GitLabApi get() {
        if (_api == null) {
            connect();
        }

        return _api;
    }
}
package org.jenkinsci.plugins.gitlab;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Commit;
import org.gitlab4j.api.models.MergeRequest;
import org.gitlab4j.api.models.Note;
import org.gitlab4j.api.models.Project;

public class GitlabMergeRequestWrapper {

    private static final Logger _logger = Logger.getLogger(GitlabMergeRequestWrapper.class.getName());
    private final Integer _id;
    private final String _author;
    private String _source;
    private String _target;

    private boolean _shouldRun = false;

    transient private Project _project;
    transient private GitlabMergeRequestBuilder _builder;


    GitlabMergeRequestWrapper(MergeRequest mergeRequest, GitlabMergeRequestBuilder builder, Project project) {
        _id = mergeRequest.getId();
        _author = mergeRequest.getAuthor().getUsername();
        _source = mergeRequest.getSourceBranch();
        _target = mergeRequest.getTargetBranch();
        _project = project;
        _builder = builder;
    }

    public void init(GitlabMergeRequestBuilder builder, Project project) {
        _project = project;
        _builder = builder;
    }

    public void check(MergeRequest gitlabMergeRequest) {
        if (_target == null) {
            _target = gitlabMergeRequest.getTargetBranch();
        }

        if (_source == null) {
            _source = gitlabMergeRequest.getSourceBranch();
        }

        try {
            GitLabApi api = _builder.getGitlab().get();
            Note lastJenkinsNote = getJenkinsNote(gitlabMergeRequest, api);

            if (lastJenkinsNote == null) {
                _shouldRun = true;
            } else {
                Commit latestCommit = getLatestCommit(gitlabMergeRequest, api);

                if (latestCommit != null) {
                    _shouldRun = latestCommit.getCreatedAt().after(lastJenkinsNote.getCreatedAt());
                }
            }
        } catch (GitLabApiException e) {
            _logger.log(Level.SEVERE, "Failed to fetch commits for Merge Request " + gitlabMergeRequest.getId(), e);
        }

        if (_shouldRun) {
            build();
        }
    }

    private Note getJenkinsNote(MergeRequest gitlabMergeRequest, GitLabApi api) throws GitLabApiException {
        List<Note> notes = api.getNotesApi().getMergeRequestNotes(_project.getId(), gitlabMergeRequest.getIid());
        Note lastJenkinsNote = null;

        if (!notes.isEmpty()) {
            Collections.sort(notes, new Comparator<Note>() {
                public int compare(Note o1, Note o2) {
                    return o2.getCreatedAt().compareTo(o1.getCreatedAt());
                }
            });

            for (Note note : notes) {
                if (note.getAuthor().getUsername().equals(GitlabBuildTrigger.getDesc().getBotUsername())) {
                    lastJenkinsNote = note;
                    break;
                }
            }
        }
        return lastJenkinsNote;
    }

    private Commit getLatestCommit(MergeRequest gitlabMergeRequest, GitLabApi api) throws GitLabApiException {
        List<Commit> commits = api.getMergeRequestApi().getCommits(_project.getId(), gitlabMergeRequest.getIid());
        Collections.sort(commits, new Comparator<Commit>() {
            public int compare(Commit o1, Commit o2) {
                return o2.getCreatedAt().compareTo(o1.getCreatedAt());
            }
        });

        if (commits.isEmpty()) {
            _logger.log(Level.SEVERE, "Merge Request without commits.");
            return null;
        }

        return commits.get(0);
    }

    public Integer getId() {
        return _id;
    }

    public String getAuthor() {
        return _author;
    }

    public String getSource() {
        return _source;
    }

    public String getTarget() {
        return _target;
    }

    public Note createNote(String message) {
        try {
            return _builder.getGitlab().get().getNotesApi().createMergeRequestNote(_project.getId(), _id, message);
        } catch (GitLabApiException e) {
            _logger.log(Level.SEVERE, "Failed to create note for merge request " + _id, e);
            return null;
        }
    }

    private void build() {
        _shouldRun = false;
        String message = _builder.getBuilds().build(this);

        if (_builder.isEnableBuildTriggeredMessage()) {
            createNote(message);
            _logger.log(Level.INFO, message);
        }
    }

}
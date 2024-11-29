package org.tudalgo.algoutils.tutor.general;

import java.util.List;
import java.util.Map;

public record SubmissionInfo(
    String assignmentId,
    String jagrVersion,
    List<SourceSet> sourceSets,
    DependencyConfiguration dependencyConfigurations,
    List<RepositoryConfiguration> repositoryConfigurations,
    String studentId,
    String firstName,
    String lastName
) {
    public record SourceSet(String name, Map<String, List<String>> files) {}

    public record DependencyConfiguration(List<String> implementation, List<String> testImplementation) {}

    public record RepositoryConfiguration(String name, String url) {}
}

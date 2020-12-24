/*-
 * #%L
 * artifact-set-resolver
 * %%
 * Copyright (C) 2014 - 2020 Andreas Veithen
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package com.github.veithen.maven.shared.artifactset;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.filter.AndArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Repository;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.shared.transfer.artifact.DefaultArtifactCoordinate;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolver;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolverException;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.StringUtils;

@Component(role = ArtifactSetResolver.class, hint = "default")
public class DefaultArtifactSetResolver implements ArtifactSetResolver {
    @Requirement private RepositorySystem repositorySystem;

    @Requirement private ArtifactResolver resolver;

    @Requirement private Logger logger;

    @Override
    public List<Artifact> resolveArtifactSet(
            MavenProject project,
            MavenSession session,
            ArtifactSet artifactSet,
            Repository[] repositories)
            throws ArtifactSetResolverException {
        List<Artifact> resolvedArtifacts = new ArrayList<Artifact>();

        if (artifactSet != null) {
            DependencySet dependencySet = artifactSet.getDependencySet();
            if (dependencySet != null) {
                if (logger.isDebugEnabled()) {
                    logger.debug(
                            "Resolving project dependencies in scope " + dependencySet.getScope());
                }
                AndArtifactFilter filter = new AndArtifactFilter();
                filter.add(new ScopeArtifactFilter(dependencySet.getScope()));
                filter.add(
                        new IncludeExcludeArtifactFilter(
                                dependencySet.getIncludes(), dependencySet.getExcludes()));
                for (Artifact artifact : project.getArtifacts()) {
                    if (filter.include(artifact)) {
                        resolvedArtifacts.add(artifact);
                    } else if (logger.isDebugEnabled()) {
                        logger.debug(
                                "Artifact "
                                        + artifact.getDependencyConflictId()
                                        + " not selected by filter");
                    }
                }
                if (dependencySet.isUseProjectArtifact()) {
                    resolvedArtifacts.add(project.getArtifact());
                }
            }

            List<ArtifactItem> artifacts = artifactSet.getArtifacts();
            if (artifacts != null && !artifacts.isEmpty()) {
                DefaultProjectBuildingRequest projectBuildingRequest =
                        new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
                List<ArtifactRepository> remoteRepositories =
                        new ArrayList<ArtifactRepository>(
                                projectBuildingRequest.getRemoteRepositories());
                if (repositories != null && repositories.length > 0) {
                    for (Repository repository : repositories) {
                        try {
                            remoteRepositories.add(
                                    repositorySystem.buildArtifactRepository(repository));
                        } catch (InvalidRepositoryException ex) {
                            throw new ArtifactSetResolverException("Invalid repository", ex);
                        }
                    }
                }
                projectBuildingRequest.setRemoteRepositories(remoteRepositories);
                for (ArtifactItem artifactItem : artifacts) {
                    String version = artifactItem.getVersion();
                    if (StringUtils.isEmpty(version)) {
                        version = getMissingArtifactVersion(project, artifactItem);
                    }
                    DefaultArtifactCoordinate artifact = new DefaultArtifactCoordinate();
                    artifact.setGroupId(artifactItem.getGroupId());
                    artifact.setArtifactId(artifactItem.getArtifactId());
                    artifact.setVersion(version);
                    artifact.setExtension(artifactItem.getType());
                    artifact.setClassifier(artifactItem.getClassifier());
                    try {
                        resolvedArtifacts.add(
                                resolver.resolveArtifact(projectBuildingRequest, artifact)
                                        .getArtifact());
                    } catch (ArtifactResolverException ex) {
                        throw new ArtifactSetResolverException("Unable to resolve artifact", ex);
                    }
                }
            }
        }

        return resolvedArtifacts;
    }

    private String getMissingArtifactVersion(MavenProject project, ArtifactItem artifact)
            throws ArtifactSetResolverException {
        List<Dependency> dependencies = project.getDependencies();
        List<Dependency> managedDependencies =
                project.getDependencyManagement() == null
                        ? null
                        : project.getDependencyManagement().getDependencies();
        String version = findDependencyVersion(artifact, dependencies, false);
        if (version == null && managedDependencies != null) {
            version = findDependencyVersion(artifact, managedDependencies, false);
        }
        if (version == null) {
            version = findDependencyVersion(artifact, dependencies, true);
        }
        if (version == null && managedDependencies != null) {
            version = findDependencyVersion(artifact, managedDependencies, true);
        }
        if (version == null) {
            throw new ArtifactSetResolverException(
                    "Unable to find artifact version of "
                            + artifact.getGroupId()
                            + ":"
                            + artifact.getArtifactId()
                            + " in either dependency list or in project's dependency management.");
        } else {
            return version;
        }
    }

    private String findDependencyVersion(
            ArtifactItem artifact, List<Dependency> dependencies, boolean looseMatch) {
        for (Dependency dependency : dependencies) {
            if (Objects.equals(dependency.getArtifactId(), artifact.getArtifactId())
                    && Objects.equals(dependency.getGroupId(), artifact.getGroupId())
                    && (looseMatch
                            || Objects.equals(dependency.getClassifier(), artifact.getClassifier()))
                    && (looseMatch || Objects.equals(dependency.getType(), artifact.getType()))) {
                return dependency.getVersion();
            }
        }
        return null;
    }
}

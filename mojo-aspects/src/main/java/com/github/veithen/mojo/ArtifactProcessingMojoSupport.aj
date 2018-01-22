/*-
 * #%L
 * Shared Maven utilities
 * %%
 * Copyright (C) 2014 - 2018 Andreas Veithen
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
package com.github.veithen.mojo;

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.filter.AndArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Repository;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.shared.artifact.resolve.ArtifactResolver;
import org.apache.maven.shared.artifact.resolve.ArtifactResolverException;
import org.codehaus.plexus.util.StringUtils;

public aspect ArtifactProcessingMojoSupport {
    @Parameter(property="project", required=true, readonly=true)
    private MavenProject ArtifactProcessingMojo.project;
    
    @Parameter
    private DependencySet ArtifactProcessingMojo.dependencySet;

    @Parameter
    private ArtifactItem[] ArtifactProcessingMojo.artifacts;
    
    @Parameter
    private Repository[] ArtifactProcessingMojo.repositories;
    
    @Parameter(property="session", required=true, readonly=true)
    private MavenSession ArtifactProcessingMojo.session;

    @Component
    private RepositorySystem ArtifactProcessingMojo.repositorySystem;
    
    @Component
    private ArtifactResolver ArtifactProcessingMojo.resolver;

    public List<Artifact> ArtifactProcessingMojo.resolveArtifacts() throws MojoExecutionException {
        List<Artifact> resolvedArtifacts = new ArrayList<Artifact>();

        Log log = getLog();

        if (dependencySet != null) {
            if (log.isDebugEnabled()) {
                log.debug("Resolving project dependencies in scope " + dependencySet.getScope());
            }
            AndArtifactFilter filter = new AndArtifactFilter();
            filter.add(new ScopeArtifactFilter(dependencySet.getScope()));
            filter.add(new IncludeExcludeArtifactFilter(dependencySet.getIncludes(), dependencySet.getExcludes(), null));
            for (Artifact artifact : project.getArtifacts()) {
                if (filter.include(artifact)) {
                    resolvedArtifacts.add(artifact);
                }
            }
            if (dependencySet.isUseProjectArtifact()) {
                resolvedArtifacts.add(project.getArtifact());
            }
        }
        
        if (artifacts != null && artifacts.length != 0) {
            DefaultProjectBuildingRequest projectBuildingRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
            List<ArtifactRepository> remoteRepositories = new ArrayList<ArtifactRepository>(projectBuildingRequest.getRemoteRepositories());
            if (repositories != null && repositories.length > 0) {
                for (Repository repository : repositories) {
                    try {
                        remoteRepositories.add(repositorySystem.buildArtifactRepository(repository));
                    } catch (InvalidRepositoryException ex) {
                        throw new MojoExecutionException("Invalid repository", ex);
                    }
                }
            }
            projectBuildingRequest.setRemoteRepositories(remoteRepositories);
            for (ArtifactItem artifactItem : artifacts) {
                String version = artifactItem.getVersion();
                if (StringUtils.isEmpty(version)) {
                    version = getMissingArtifactVersion(artifactItem);
                }
                Dependency dependency = new Dependency();
                dependency.setGroupId(artifactItem.getGroupId());
                dependency.setArtifactId(artifactItem.getArtifactId());
                dependency.setVersion(version);
                dependency.setType(artifactItem.getType());
                dependency.setClassifier(artifactItem.getClassifier());
                dependency.setScope(Artifact.SCOPE_COMPILE);
                Artifact artifact = repositorySystem.createDependencyArtifact(dependency);
                try {
                    resolvedArtifacts.add(resolver.resolveArtifact(projectBuildingRequest, artifact).getArtifact());
                } catch (ArtifactResolverException ex) {
                    throw new MojoExecutionException("Unable to resolve artifact", ex);
                }
            }
        }
        
        return resolvedArtifacts;
    }
    
    private String ArtifactProcessingMojo.getMissingArtifactVersion(ArtifactItem artifact) throws MojoExecutionException {
        List<Dependency> dependencies = project.getDependencies();
        List<Dependency> managedDependencies = project.getDependencyManagement() == null ? null
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
            throw new MojoExecutionException(
                "Unable to find artifact version of " + artifact.getGroupId() + ":" + artifact.getArtifactId()
                    + " in either dependency list or in project's dependency management." );
        } else {
            return version;
        }
    }

    private String ArtifactProcessingMojo.findDependencyVersion(ArtifactItem artifact, List<Dependency> dependencies, boolean looseMatch) {
        for (Dependency dependency : dependencies) {
            if (StringUtils.equals(dependency.getArtifactId(), artifact.getArtifactId())
                && StringUtils.equals(dependency.getGroupId(), artifact.getGroupId())
                && (looseMatch || StringUtils.equals(dependency.getClassifier(), artifact.getClassifier()))
                && (looseMatch || StringUtils.equals(dependency.getType(), artifact.getType()))) {
                return dependency.getVersion();
            }
        }
        return null;
    }
}

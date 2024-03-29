/*
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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;

import java.util.Collection;
import java.util.HashSet;

/** An artifact filter for full include and exclude. Derived from code in the maven-shade-plugin. */
class IncludeExcludeArtifactFilter implements ArtifactFilter {

    private Collection<ArtifactId> includes;

    private Collection<ArtifactId> excludes;

    public IncludeExcludeArtifactFilter(Collection<String> includes, Collection<String> excludes) {
        this.includes = toIds(includes);
        this.excludes = toIds(excludes);
    }

    private static Collection<ArtifactId> toIds(Collection<String> patterns) {
        Collection<ArtifactId> result = new HashSet<ArtifactId>();

        if (patterns != null) {
            for (String pattern : patterns) {
                result.add(new ArtifactId(pattern));
            }
        }

        return result;
    }

    private static boolean matches(Collection<ArtifactId> patterns, ArtifactId id) {
        for (ArtifactId pattern : patterns) {
            if (id.matches(pattern)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean include(Artifact artifact) {
        if (artifact == null) {
            return false;
        }
        ArtifactId id = new ArtifactId(artifact);
        return (includes.isEmpty() || matches(includes, id)) && !matches(excludes, id);
    }
}

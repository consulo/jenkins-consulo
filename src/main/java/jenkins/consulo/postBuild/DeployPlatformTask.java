/*
 * Copyright 2013-2016 must-be.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jenkins.consulo.postBuild;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.Collections;

/**
 * @author VISTALL
 * @since 29-Aug-16
 */
public class DeployPlatformTask extends DeployArtifactTaskBase
{
	@Extension
	public static class DescriptorImpl extends DeployDescriptorBase
	{
		@Override
		public String getDisplayName()
		{
			return "Consulo/Deploy platform artifacts to repository";
		}
	}

	@DataBoundConstructor
	public DeployPlatformTask(String repositoryUrl, boolean enableRepositoryUrl, String pluginChannel, boolean allowUnstable)
	{
		super(repositoryUrl, enableRepositoryUrl, pluginChannel, allowUnstable);
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException
	{
		Result result = build.getResult();
		if(result == null || (allowUnstable ? result.isWorseThan(Result.UNSTABLE) : result.isWorseOrEqualTo(Result.UNSTABLE)))
		{
			throw new IOException("Project is not build");
		}

		ArtifactPaths artifactPaths = ArtifactPaths.find(build, listener);

		FilePath workspace = build.getWorkspace();
		FilePath allArtifactsDir = workspace.child(artifactPaths.getAllArtifactsPath());
		if(!allArtifactsDir.exists())
		{
			throw new IOException("No artifacts");
		}

		int artifactCount = 0;
		for(FilePath artifactPath : allArtifactsDir.list())
		{
			if(artifactPath.isDirectory())
			{
				continue;
			}

			String baseName = artifactPath.getBaseName();

			if(!baseName.startsWith("consulo.dist."))
			{
				continue;
			}

			artifactCount += deployArtifact("platformDeploy", Collections.singletonMap("platformVersion", String.valueOf(build.getNumber())), artifactPath, listener, build, artifactCount);
		}

		if(artifactCount == 0)
		{
			throw new IOException("No artifacts for deploy");
		}
		return true;
	}
}

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
import hudson.maven.MavenModule;
import hudson.maven.MavenModuleSet;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

/**
 * @author VISTALL
 * @since 29-Aug-16
 */
public class DeployPluginTask extends DeployArtifactTaskBase
{
	@Extension
	public static class DescriptorImpl extends DeployDescriptorBase
	{
		@Override
		public String getDisplayName()
		{
			return "Deploy plugin artifacts to repository (Consulo)";
		}
	}

	@DataBoundConstructor
	public DeployPluginTask(String repositoryUrl, boolean enableRepositoryUrl, String pluginChannel, boolean allowUnstable)
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

		int artifactCount = 0;

		FilePath workspace = build.getWorkspace();
		if(workspace == null)
		{
			throw new IOException("Workspace is null");
		}

		AbstractProject<?, ?> project = build.getProject();
		if(project instanceof MavenModuleSet)
		{
			listener.getLogger().println("Deploying mavenize plugins");

			Collection<MavenModule> modules = ((MavenModuleSet) project).getModules();
			for(MavenModule module : modules)
			{
				if(module.isDisabled())
				{
					continue;
				}

				String packaging = module.getPackaging();

				if("consulo-plugin".equals(packaging))
				{
					FilePath targetDirectory = workspace.child(module.getRelativePath().isEmpty() ? "target" : (module.getRelativePath() + "/target"));
					if(targetDirectory.exists())
					{
						for(FilePath artifactPath : targetDirectory.list("*.consulo-plugin"))
						{
							artifactCount += deployArtifact("pluginDeploy", Collections.<String, String>emptyMap(),  artifactPath, listener, build, artifactCount);
						}
					}
				}
			}
		}

		if(artifactCount == 0)
		{
			throw new IOException("No artifacts for deploy");
		}
		return true;
	}
}

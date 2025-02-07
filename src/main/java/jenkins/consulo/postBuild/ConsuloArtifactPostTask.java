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
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import jakarta.annotation.Nonnull;
import jenkins.consulo.postBuild.consuloArtifactTask.Generator;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

/**
 * @author VISTALL
 * @since 04-Sep-16
 */
public class ConsuloArtifactPostTask extends Notifier
{
	@Extension
	public static class DescriptorImpl extends BuildStepDescriptor<Publisher>
	{
		public DescriptorImpl()
		{
			load();
		}

		@Nonnull
		@Override
		public String getDisplayName()
		{
			return "Consulo/Create platform artifacts";
		}

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType)
		{
			return true;
		}
	}

	private String winJre32Path;
	private String winJre64Path;
	private String winJreA64Path;

	private String linuxJre32Path;
	private String linuxJre64Path;
	private String linuxJreA64Path;
	private String linuxJreLoong64Path;
	private String linuxJreRiscv64Path;

	private String macJre64Path;
	private String macJreA64Path;

	private String winJre64Nsis;

	@DataBoundConstructor
	public ConsuloArtifactPostTask(String winJre32Path,
								   String winJreA64Path,
								   String winJre64Path,
								   String linuxJre32Path,
								   String linuxJre64Path,
								   String linuxJreA64Path,
								   String linuxJreLoong64Path,
								   String linuxJreRiscv64Path,
								   String macJre64Path,
								   String macJreA64Path,
								   String winJre64Nsis)
	{
		this.winJre32Path = winJre32Path;
		this.winJre64Path = winJre64Path;
		this.winJreA64Path = winJreA64Path;

		this.linuxJre32Path = linuxJre32Path;
		this.linuxJre64Path = linuxJre64Path;
		this.linuxJreA64Path = linuxJreA64Path;
		this.linuxJreLoong64Path = linuxJreLoong64Path;
		this.linuxJreRiscv64Path = linuxJreRiscv64Path;

		this.macJre64Path = macJre64Path;
		this.macJreA64Path = macJreA64Path;

		this.winJre64Nsis = winJre64Nsis;
	}

	public String getLinuxJreLoong64Path()
	{
		return linuxJreLoong64Path;
	}

	public String getWinJre32Path()
	{
		return winJre32Path;
	}

	public String getWinJreA64Path()
	{
		return winJreA64Path;
	}

	public String getWinJre64Path()
	{
		return winJre64Path;
	}

	public String getLinuxJre32Path()
	{
		return linuxJre32Path;
	}

	public String getLinuxJre64Path()
	{
		return linuxJre64Path;
	}

	public String getMacJre64Path()
	{
		return macJre64Path;
	}

	public String getWinJre64Nsis()
	{
		return winJre64Nsis;
	}

	public String getMacJreA64Path()
	{
		return macJreA64Path;
	}

	public String getLinuxJreA64Path()
	{
		return linuxJreA64Path;
	}

	public String getLinuxJreRiscv64Path()
	{
		return linuxJreRiscv64Path;
	}

	@Override
	public BuildStepMonitor getRequiredMonitorService()
	{
		return BuildStepMonitor.BUILD;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException
	{
		Result result = build.getResult();
		if(result == null || result.isWorseThan(Result.UNSTABLE))
		{
			throw new IOException("Project is not build");
		}

		ArtifactPaths artifactPaths = ArtifactPaths.find(build, listener);

		FilePath workspace = build.getWorkspace();

		FilePath targetDir = workspace.child(artifactPaths.getAllArtifactsPath());

		if(targetDir.exists())
		{
			targetDir.deleteContents();
		}
		else
		{
			targetDir.mkdirs();
		}

		FilePath distDir = workspace.child(artifactPaths.getRawArtifactsPath());
		if(!distDir.exists())
		{
			throw new IOException("Project is not build");
		}

		FilePath jreDirectory = workspace.child("distribution/target/jre");

		if(jreDirectory.exists())
		{
			jreDirectory.deleteContents();
		}
		else
		{
			jreDirectory.mkdirs();
		}

		Generator generator = new Generator(distDir, targetDir, jreDirectory, build.getNumber(), listener);

		try
		{
			// win no jre
			generator.buildDistributionInArchive(artifactPaths.winX64ArtifactName(), null, "consulo.dist.windows.no.jre.zip", ArchiveStreamFactory.ZIP);
			generator.buildDistributionInArchive(artifactPaths.winX64ArtifactName(), null, "consulo.dist.windows.no.jre", ArchiveStreamFactory.TAR);  // archive for platformDeploy

			// win 32 bit
			if(!StringUtils.isBlank(winJre32Path))
			{
				generator.buildDistributionInArchive(artifactPaths.winX64ArtifactName(), winJre32Path, "consulo.dist.windows", ArchiveStreamFactory.ZIP);
				generator.buildDistributionInArchive(artifactPaths.winX64ArtifactName(), winJre32Path, "consulo.dist.windows.zip", ArchiveStreamFactory.TAR); // archive for platformDeploy
			}

			// win 64 bit
			generator.buildDistributionInArchive(artifactPaths.winX64ArtifactName(), winJre64Path, "consulo.dist.windows64.zip", ArchiveStreamFactory.ZIP);
			generator.buildDistributionInArchive(artifactPaths.winX64ArtifactName(), winJre64Path, "consulo.dist.windows64", ArchiveStreamFactory.TAR); // archive for platformDeploy

			if(!StringUtils.isBlank(winJre64Nsis))
			{
				// distribution/src\nsis/x64
				generator.buildWindowsInstaller(build.getWorkspace(), artifactPaths.winX64ArtifactName(), winJre64Path, winJre64Nsis, "consulo.dist.windows64.installer");
			}

			// win A64 bit
			if(!StringUtils.isBlank(winJreA64Path))
			{
				generator.buildDistributionInArchive(artifactPaths.winX64ArtifactName(), winJreA64Path, "consulo.dist.windows.aarch64.zip", ArchiveStreamFactory.ZIP);
				generator.buildDistributionInArchive(artifactPaths.winX64ArtifactName(), winJreA64Path, "consulo.dist.windows.aarch64", ArchiveStreamFactory.TAR); // archive for platformDeploy
			}

			// linux no jre
			generator.buildDistributionInArchive(artifactPaths.linuxArtifactName(), null, "consulo.dist.linux.no.jre", ArchiveStreamFactory.TAR);

			// linux x86
			if(!StringUtils.isBlank(linuxJre32Path))
			{
				generator.buildDistributionInArchive(artifactPaths.linuxArtifactName(), linuxJre32Path, "consulo.dist.linux", ArchiveStreamFactory.TAR);
			}

			// linux aarch64
			if(!StringUtils.isBlank(linuxJreA64Path))
			{
				generator.buildDistributionInArchive(artifactPaths.linuxArtifactName(), linuxJreA64Path, "consulo.dist.linux.aarch64", ArchiveStreamFactory.TAR);
			}

			// loongarch64
			if(!StringUtils.isBlank(linuxJreLoong64Path))
			{
				generator.buildDistributionInArchive(artifactPaths.linuxArtifactName(), linuxJreLoong64Path, "consulo.dist.linux.loong64", ArchiveStreamFactory.TAR);
			}

			// riscv64
			if(!StringUtils.isBlank(linuxJreRiscv64Path))
			{
				generator.buildDistributionInArchive(artifactPaths.linuxArtifactName(), linuxJreRiscv64Path, "consulo.dist.linux.riscv64", ArchiveStreamFactory.TAR);
			}

			generator.buildDistributionInArchive(artifactPaths.linuxArtifactName(), linuxJre64Path, "consulo.dist.linux64", ArchiveStreamFactory.TAR);

			// mac
			generator.buildDistributionInArchive(artifactPaths.macX64ArtifactName(), null, "consulo.dist.mac64.no.jre", ArchiveStreamFactory.TAR);
			generator.buildDistributionInArchive(artifactPaths.macX64ArtifactName(), macJre64Path, "consulo.dist.mac64", ArchiveStreamFactory.TAR);

			if(!StringUtils.isBlank(macJreA64Path))
			{
				generator.buildDistributionInArchive(artifactPaths.macA64ArtifactName(), null, "consulo.dist.macA64.no.jre", ArchiveStreamFactory.TAR);
				generator.buildDistributionInArchive(artifactPaths.macA64ArtifactName(), macJreA64Path, "consulo.dist.macA64", ArchiveStreamFactory.TAR);
			}
		}
		catch(Exception throwable)
		{
			throw new IOException(throwable);
		}
		return true;
	}
}

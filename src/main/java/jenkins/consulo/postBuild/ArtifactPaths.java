/*
 * Copyright 2013-2017 must-be.org
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

import java.io.IOException;

import javax.annotation.Nonnull;

import hudson.FilePath;
import hudson.model.AbstractBuild;

/**
 * @author VISTALL
 * @since 29-Aug-17
 */
public class ArtifactPaths
{
	@Nonnull
	public static ArtifactPaths find(AbstractBuild<?, ?> build) throws InterruptedException, IOException
	{
		FilePath workspace = build.getWorkspace();

		// maven project
		if(workspace.child("pom.xml").exists())
		{
			int number = build.getNumber();
			String win = "consulo-bundle-" + number + "-win.zip";
			String linux = "consulo-bundle-" + number + "-linux.zip";
			String mac = "consulo-bundle-" + number + "-mac.zip";
			return new ArtifactPaths("distribution/target/all", "distribution/target", win, linux, mac);
		}
		else
		{
			return new ArtifactPaths("out/artifacts/all", "out/artifacts/dist", "consulo-win.zip", "consulo-linux.zip", "consulo-mac.zip");
		}
	}

	private final String myAllArtifactsPath;
	private final String myRawArtifactsPath;
	private String myWin;
	private String myLinux;
	private String myMac;

	private ArtifactPaths(String allArtifactsPath, String rawArtifactsPath, String win, String linux, String mac)
	{
		myAllArtifactsPath = allArtifactsPath;
		myRawArtifactsPath = rawArtifactsPath;
		myWin = win;
		myLinux = linux;
		myMac = mac;
	}

	public String getWin()
	{
		return myWin;
	}

	public String getLinux()
	{
		return myLinux;
	}

	public String getMac()
	{
		return myMac;
	}

	public String getAllArtifactsPath()
	{
		return myAllArtifactsPath;
	}

	public String getRawArtifactsPath()
	{
		return myRawArtifactsPath;
	}
}

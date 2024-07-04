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

import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import jakarta.annotation.Nonnull;

import java.io.IOException;

/**
 * @author VISTALL
 * @since 29-Aug-17
 */
public class ArtifactPaths
{
	private static final String _3_SNAPSHOT = "3-SNAPSHOT";

	@Nonnull
	public static ArtifactPaths find(AbstractBuild<?, ?> build, BuildListener listener) throws InterruptedException, IOException
	{
		String win = "consulo-bundle-" + _3_SNAPSHOT + "-desktop-awt-win.zip";
		String linux = "consulo-bundle-" + _3_SNAPSHOT + "-desktop-awt-linux.zip";
		String macX64 = "consulo-bundle-" + _3_SNAPSHOT + "-desktop-awt-mac-x86-64.zip";
		String macA64 = "consulo-bundle-" + _3_SNAPSHOT + "-desktop-awt-mac-aarch64.zip";
		return new ArtifactPaths("distribution/target/all", "distribution/target", win, linux, macX64, macA64);
	}

	private final String myAllArtifactsPath;
	private final String myRawArtifactsPath;

	private final String myWin;
	private final String myLinux;
	private final String myMac64;
	private final String myMacA64;

	private ArtifactPaths(String allArtifactsPath, String rawArtifactsPath, String win, String linux, String mac64, String macA64)
	{
		myAllArtifactsPath = allArtifactsPath;
		myRawArtifactsPath = rawArtifactsPath;
		myWin = win;
		myLinux = linux;
		myMac64 = mac64;
		myMacA64 = macA64;
	}

	public String getWin()
	{
		return myWin;
	}

	public String getLinux()
	{
		return myLinux;
	}

	public String getMacX64()
	{
		return myMac64;
	}

	public String getMacA64()
	{
		return myMacA64;
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

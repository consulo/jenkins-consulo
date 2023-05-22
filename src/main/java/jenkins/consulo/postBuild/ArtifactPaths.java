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

import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import jakarta.annotation.Nonnull;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import java.io.IOException;
import java.io.StringReader;

/**
 * @author VISTALL
 * @since 29-Aug-17
 */
public class ArtifactPaths
{
	private static final String _2_SNAPSHOT = "2-SNAPSHOT";
	private static final String _3_SNAPSHOT = "3-SNAPSHOT";

	@Nonnull
	public static ArtifactPaths find(AbstractBuild<?, ?> build, BuildListener listener) throws InterruptedException, IOException
	{
		FilePath workspace = build.getWorkspace();

		// maven project
		FilePath pom = workspace.child("pom.xml");
		if(pom.exists())
		{
			boolean is3Version = false;
			try
			{
				SAXReader saxBuilder = new SAXReader(false);
				Document document = saxBuilder.read(new StringReader(pom.readToString()));
				Element version = document.getRootElement().element("version");
				if(version != null)
				{
					String ver = version.getTextTrim();
					listener.getLogger().println("Artifact version: " + ver);
					is3Version = _3_SNAPSHOT.equals(ver);
				}
			}
			catch(Exception ignored)
			{
			}

			if(is3Version)
			{
				String win = "consulo-bundle-" + _3_SNAPSHOT + "-desktop-awt-win.zip";
				String linux = "consulo-bundle-" + _3_SNAPSHOT + "-desktop-awt-linux.zip";
				String mac = "consulo-bundle-" + _3_SNAPSHOT + "-desktop-awt-mac-x86-64.zip";
				return new ArtifactPaths("distribution/target/all", "distribution/target", win, linux, mac);
			}
			else
			{
				String win = "consulo-bundle-" + _2_SNAPSHOT + "-win.zip";
				String linux = "consulo-bundle-" + _2_SNAPSHOT + "-linux.zip";
				String mac = "consulo-bundle-" + _2_SNAPSHOT + "-mac.zip";
				return new ArtifactPaths("distribution/target/all", "distribution/target", win, linux, mac);
			}
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
	private String myMac64;

	private ArtifactPaths(String allArtifactsPath, String rawArtifactsPath, String win, String linux, String mac64)
	{
		myAllArtifactsPath = allArtifactsPath;
		myRawArtifactsPath = rawArtifactsPath;
		myWin = win;
		myLinux = linux;
		myMac64 = mac64;
	}

	public String getWin()
	{
		return myWin;
	}

	public String getLinux()
	{
		return myLinux;
	}

	public String getMac64()
	{
		return myMac64;
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

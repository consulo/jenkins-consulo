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

package jenkins.consulo.postBuild.consuloArtifactTask;

import hudson.FilePath;
import hudson.model.BuildListener;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import java.io.FileInputStream;
import java.io.InputStream;

/**
 * @author VISTALL
 * @since 04-Sep-16
 */
public class OldJRE8Generator extends Generator
{
	private static final String[] ourWinLinuxSkipListFromJre = new String[]{
			"jre/lib/tools.jar"
	};

	private static final String[] ourMacSkipListFromJre = new String[]{
			"jdk/Contents/Home/demo/",
			"jdk/Contents/Home/include/",
			"jdk/Contents/Home/lib/",
			"jdk/Contents/Home/man/",
			"jdk/Contents/Home/sample/",
			"jdk/Contents/Home/src.zip",
	};

	public OldJRE8Generator(FilePath distPath, FilePath targetDir, int buildNumber, BuildListener listener)
	{
		super(distPath, targetDir, buildNumber, listener);
	}

	@Override
	protected void buildBundledJRE(String jdkArchivePath, ArchiveStreamFactory factory, String archiveOutType, ArchiveOutputStream archiveOutputStream, boolean mac) throws Exception
	{
		try (InputStream is = new FileInputStream(jdkArchivePath))
		{
			try (GzipCompressorInputStream gz = new GzipCompressorInputStream(is))
			{
				try (ArchiveInputStream ais = factory.createArchiveInputStream(ArchiveStreamFactory.TAR, gz))
				{
					ArchiveEntry tempEntry = ais.getNextEntry();
					while(tempEntry != null)
					{
						final String name = tempEntry.getName();

						// is our path
						if(!mac && name.startsWith("jre/"))
						{
							if(needAddToArchive(name, ourWinLinuxSkipListFromJre))
							{
								final ArchiveEntryWrapper jdkEntry = createEntry(archiveOutType, "Consulo/platform/buildSNAPSHOT/" + name, tempEntry);
								jdkEntry.setMode(extractMode(tempEntry));
								jdkEntry.setTime(tempEntry.getLastModifiedDate().getTime());

								copyEntry(archiveOutputStream, ais, tempEntry, jdkEntry);
							}
						}
						else if(mac && name.startsWith("jdk"))
						{
							if(needAddToArchive(name, ourMacSkipListFromJre))
							{
								final ArchiveEntryWrapper jdkEntry = createEntry(archiveOutType, "Consulo.app/Contents/platform/buildSNAPSHOT/jre/" + name, tempEntry);
								jdkEntry.setMode(extractMode(tempEntry));
								jdkEntry.setTime(tempEntry.getLastModifiedDate().getTime());

								copyEntry(archiveOutputStream, ais, tempEntry, jdkEntry);
							}
						}

						tempEntry = ais.getNextEntry();
					}
				}
			}
		}
	}
}
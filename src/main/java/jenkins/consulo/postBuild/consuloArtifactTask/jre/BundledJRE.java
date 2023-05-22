/*
 * Copyright 2013-2023 must-be.org
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

package jenkins.consulo.postBuild.consuloArtifactTask.jre;

import com.github.gino0631.xar.XarArchive;
import hudson.FilePath;
import jakarta.annotation.Nonnull;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static jenkins.consulo.postBuild.consuloArtifactTask.Generator.ourBuildSNAPSHOT;

/**
 * @author VISTALL
 * @since 22/05/2023
 */
public abstract class BundledJRE<E>
{
	private static final String[] ourWinLinuxSkipListFromJre = {
			"jmods",
			"lib/src.zip"
	};

	private static final String[] ourMacSkipListFromJre = {
			"Contents/Home/jmods/",
			"Contents/Home/lib/src.zip",
	};

	private int myBuildNumber;
	private final ArchiveStreamFactory myArchiveStreamFactory;
	private final FilePath myJdkArchivePath;
	private final boolean myIsMac;

	public BundledJRE(int buildNumber, ArchiveStreamFactory archiveStreamFactory, FilePath jdkArchivePath, boolean isMac)
	{
		myBuildNumber = buildNumber;
		myArchiveStreamFactory = archiveStreamFactory;
		myJdkArchivePath = jdkArchivePath;
		myIsMac = isMac;
	}

	public void build() throws Exception
	{
		final String[] rootDirectoryRef = new String[2];

		openAndProcessJreArchive(myJdkArchivePath, myArchiveStreamFactory, new ArchiveInputStreamProcessor()
		{
			@Override
			public void run(ArchiveInputStream stream) throws IOException
			{
				ArchiveEntry tempEntry = stream.getNextEntry();
				while(tempEntry != null)
				{
					final String name = skipStartEntry(tempEntry.getName());
					if(!name.isEmpty())
					{
						if(tempEntry.isDirectory())
						{
							rootDirectoryRef[0] = name;
							rootDirectoryRef[1] = tempEntry.getName();
							break;
						}
					}

					tempEntry = stream.getNextEntry();
				}
			}
		});

		if(rootDirectoryRef[0] == null)
		{
			throw new IOException("Can't find root directory");
		}

		// if jre archive is zip, path will be jdk-17/
		final String rootPath = rootDirectoryRef[0];
		// but if archive is tar, path will be ./jdk-17/, we need it for correct skipping
		final String originalRootPath = rootDirectoryRef[1];

		openAndProcessJreArchive(myJdkArchivePath, myArchiveStreamFactory, new ArchiveInputStreamProcessor()
		{
			@Override
			public void run(ArchiveInputStream ais) throws IOException
			{
				ArchiveEntry tempEntry = ais.getNextEntry();
				while(tempEntry != null)
				{
					final String name = skipStartEntry(tempEntry.getName());

					if(isUselessFile(name))
					{
						tempEntry = ais.getNextEntry();
						continue;
					}

					String[] skipSuffixes = myIsMac ? ourMacSkipListFromJre : ourWinLinuxSkipListFromJre;
					Set<String> skipPaths = new HashSet<>();
					for(String skip : skipSuffixes)
					{
						skipPaths.add(rootPath + skip);
					}

					if(myIsMac)
					{
						// dot used in pkg, without root entry like jdk17_1
						if(name.startsWith(rootPath) || rootPath.equals("."))
						{
							// do not allow replace dot for all files inside archive
							String correctName = rootPath.equals(".") ? "jdk/" + name : name.replace(rootPath, "jdk/");

							if(needAddToArchive(name, skipPaths))
							{
								ArchiveEntryWrapper<? extends E> jdkEntry = createEntry("Consulo.app/Contents/platform/buildSNAPSHOT/jre/" + correctName, tempEntry);
								jdkEntry.setMode(extractMode(tempEntry));
								jdkEntry.setTime(tempEntry.getLastModifiedDate().getTime());

								copyEntry(ais, tempEntry, jdkEntry);
							}
						}
					}
					else
					{
						if(name.startsWith(rootPath))
						{
							if(needAddToArchive(name, skipPaths))
							{
								String correctName = name.replace(rootPath, "jre/");

								final ArchiveEntryWrapper<? extends E> jdkEntry = createEntry("Consulo/platform/buildSNAPSHOT/" + correctName, tempEntry);
								jdkEntry.setMode(extractMode(tempEntry));
								jdkEntry.setTime(tempEntry.getLastModifiedDate().getTime());

								copyEntry(ais, tempEntry, jdkEntry);
							}
						}
					}

					tempEntry = ais.getNextEntry();
				}
			}
		});
	}

	@Nonnull
	protected abstract ArchiveEntryWrapper<? extends E> createEntry(String name, ArchiveEntry tempEntry);

	protected abstract void copyEntry(ArchiveInputStream ais, ArchiveEntry tempEntry, ArchiveEntryWrapper<? extends E> newEntry) throws IOException;

	protected String replaceBuildDirectory(String entryName)
	{
		if(entryName.contains(ourBuildSNAPSHOT))
		{
			return entryName.replace(ourBuildSNAPSHOT, "build" + myBuildNumber);
		}
		return entryName;
	}


	protected static boolean needAddToArchive(String name, Set<String> paths)
	{
		for(String prefix : paths)
		{
			if(name.startsWith(prefix))
			{
				return false;
			}
		}
		return true;
	}

	protected static int extractMode(ArchiveEntry entry)
	{
		if(entry instanceof TarArchiveEntry)
		{
			return ((TarArchiveEntry) entry).getMode();
		}
		return entry.isDirectory() ? TarArchiveEntry.DEFAULT_DIR_MODE : TarArchiveEntry.DEFAULT_FILE_MODE;
	}

	private static void openAndProcessJreArchive(FilePath jdkArchivePath,
												 ArchiveStreamFactory factory,
												 ArchiveInputStreamProcessor processor) throws IOException, ArchiveException, InterruptedException
	{
		if(jdkArchivePath.getName().endsWith(".zip"))
		{
			try (InputStream is = jdkArchivePath.read())
			{
				try (ArchiveInputStream ais = factory.createArchiveInputStream(ArchiveStreamFactory.ZIP, is))
				{
					processor.run(ais);
				}
			}
		}
		else if(jdkArchivePath.getName().endsWith(".pkg"))
		{
			try (XarArchive archive = XarArchive.load(() ->
			{
				try
				{
					return jdkArchivePath.read();
				}
				catch(InterruptedException e)
				{
					throw new IOException(e);
				}
			}))
			{
				List<XarArchive.Entry> entries = archive.getEntries();

				for(XarArchive.Entry entry : entries)
				{
					if("Payload".equals(entry.getName()))
					{
						InputStream stream = entry.newInputStream();

						try (CpioArchiveInputStream archiveInputStream = new CpioArchiveInputStream(new GzipCompressorInputStream(stream)))
						{
							processor.run(archiveInputStream);
						}
					}
				}
			}
		}
		else
		{
			try (InputStream is = jdkArchivePath.read())
			{
				try (GzipCompressorInputStream gz = new GzipCompressorInputStream(is))
				{
					try (ArchiveInputStream ais = factory.createArchiveInputStream(ArchiveStreamFactory.TAR, gz))
					{
						processor.run(ais);
					}
				}
			}
		}
	}

	private static String skipStartEntry(String name)
	{
		if(name.startsWith("./"))
		{
			return name.substring(2, name.length());
		}
		return name;
	}

	private static boolean isUselessFile(String path)
	{
		return path.endsWith(".pdb") || path.endsWith(".DS_Store") || path.contains(".dSYM/") || path.equals(".");
	}
}

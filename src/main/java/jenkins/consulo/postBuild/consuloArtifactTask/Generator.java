/*
 * Copyright 2013-2019 must-be.org
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

import com.Ostermiller.util.BinaryDataException;
import com.Ostermiller.util.LineEnds;
import hudson.FilePath;
import hudson.model.BuildListener;
import jakarta.annotation.Nullable;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * @author VISTALL
 * @since 2019-11-17
 */
public abstract class Generator
{
	private static final String[] ourExecutable = new String[]{
			// linux
			"Consulo/consulo.sh",
			"Consulo/platform/buildSNAPSHOT/bin/launcher.sh",
			"Consulo/platform/buildSNAPSHOT/bin/fsnotifier",
			"Consulo/platform/buildSNAPSHOT/bin/fsnotifier64",
			// mac
			"Consulo.app/Contents/platform/buildSNAPSHOT/bin/fsnotifier",
			"Consulo.app/Contents/platform/buildSNAPSHOT/bin/restarter",
			"Consulo.app/Contents/platform/buildSNAPSHOT/bin/printenv.py",
			"Consulo.app/Contents/platform/buildSNAPSHOT/bin/printenv",
			"Consulo.app/Contents/MacOS/consulo",
	};

	protected static final String ourBuildSNAPSHOT = "buildSNAPSHOT";

	protected FilePath myDistPath;
	protected FilePath myTargetDir;
	protected FilePath myJreDirectory;
	protected int myBuildNumber;
	protected BuildListener myListener;

	public Generator(FilePath distPath, FilePath targetDir, FilePath jreDirectory, int buildNumber, BuildListener listener)
	{
		myDistPath = distPath;
		myTargetDir = targetDir;
		myJreDirectory = jreDirectory;
		myBuildNumber = buildNumber;
		myListener = listener;
	}

	public void buildDistributionInArchive(String distZip, @Nullable String jdkArchivePathOrUrl, String path, String archiveOutType) throws Exception
	{
		FilePath jdkArchivePath;

		if(jdkArchivePathOrUrl != null && jdkArchivePathOrUrl.startsWith("https://"))
		{
			myListener.getLogger().println("JRE: downloading " + jdkArchivePathOrUrl);

			URL jreUrl = new URL(jdkArchivePathOrUrl);

			HttpURLConnection connection = (HttpURLConnection) jreUrl.openConnection();

			String fileName = null;
			String contentDisposition = connection.getHeaderField("Content-Disposition");
			if(contentDisposition != null)
			{
				fileName = contentDisposition.replaceFirst("(?i)^.*filename=\"?([^\"]+)\"?.*$", "$1");
			}
			else
			{
				fileName = jdkArchivePathOrUrl.substring(jdkArchivePathOrUrl.lastIndexOf('/') + 1, jdkArchivePathOrUrl.length());
			}

			connection.disconnect();

			FilePath jreFileArchiveFile = myJreDirectory.child(fileName);

			jreFileArchiveFile.copyFrom(jreUrl);

			jdkArchivePath = jreFileArchiveFile;
		}
		else
		{
			jdkArchivePath = jdkArchivePathOrUrl == null ? null : new FilePath(new File(jdkArchivePathOrUrl));
		}

		myListener.getLogger().println("Build: " + path);

		ArchiveStreamFactory factory = new ArchiveStreamFactory();

		final FilePath fileZip = myDistPath.child(distZip);

		final List<String> executables = Arrays.asList(ourExecutable);

		try (OutputStream pathStream = createOutputStream(archiveOutType, path))
		{
			ArchiveOutputStream archiveOutputStream = factory.createArchiveOutputStream(archiveOutType, pathStream);
			if(archiveOutputStream instanceof TarArchiveOutputStream)
			{
				((TarArchiveOutputStream) archiveOutputStream).setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
			}

			// move Consulo to archive, and change permissions
			try (InputStream is = fileZip.read())
			{
				try (ArchiveInputStream ais = factory.createArchiveInputStream(ArchiveStreamFactory.ZIP, is))
				{
					ArchiveEntry tempEntry = ais.getNextEntry();
					while(tempEntry != null)
					{
						final ArchiveEntryWrapper newEntry = createEntry(archiveOutType, tempEntry.getName(), tempEntry);

						newEntry.setMode(extractMode(tempEntry));
						newEntry.setTime(tempEntry.getLastModifiedDate().getTime());

						if(executables.contains(tempEntry.getName()))
						{
							newEntry.setMode(0b111_101_101);
						}

						copyEntry(archiveOutputStream, ais, tempEntry, newEntry);

						tempEntry = ais.getNextEntry();
					}
				}
			}

			// jdk check
			if(jdkArchivePath != null)
			{
				boolean mac = distZip.contains("mac");
				buildBundledJRE(jdkArchivePath, factory, archiveOutType, archiveOutputStream, mac);
			}

			archiveOutputStream.finish();
		}
	}

	protected abstract void buildBundledJRE(FilePath jdkArchivePath, ArchiveStreamFactory factory, String archiveOutType, ArchiveOutputStream archiveOutputStream, boolean mac) throws Exception;

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

	protected static void copyEntry(ArchiveOutputStream archiveOutputStream, ArchiveInputStream ais, ArchiveEntry tempEntry, ArchiveEntryWrapper newEntry) throws IOException
	{
		byte[] data = null;
		if(!tempEntry.isDirectory())
		{
			try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream())
			{
				IOUtils.copy(ais, byteStream);
				data = byteStream.toByteArray();
			}

			// change line breaks
			try (ByteArrayOutputStream stream = new ByteArrayOutputStream())
			{
				if(LineEnds.convert(new ByteArrayInputStream(data), stream, LineEnds.STYLE_UNIX))
				{
					data = stream.toByteArray();
				}
			}
			catch(BinaryDataException ignored)
			{
				// ignore binary data
			}
		}

		if(data != null)
		{
			newEntry.setSize(data.length);
		}

		archiveOutputStream.putArchiveEntry(newEntry.getArchiveEntry());

		if(data != null)
		{
			IOUtils.copy(new ByteArrayInputStream(data), archiveOutputStream);
		}

		archiveOutputStream.closeArchiveEntry();
	}

	protected ArchiveEntryWrapper createEntry(String type, String name, ArchiveEntry tempEntry)
	{
		name = replaceBuildDirectory(name);

		if(type.equals(ArchiveStreamFactory.TAR))
		{
			return new ArchiveEntryWrapper.Tar(name, tempEntry);
		}
		return new ArchiveEntryWrapper.Zip(name);
	}

	protected String replaceBuildDirectory(String entryName)
	{
		if(entryName.contains(ourBuildSNAPSHOT))
		{
			return entryName.replace(ourBuildSNAPSHOT, "build" + myBuildNumber);
		}
		return entryName;
	}

	protected OutputStream createOutputStream(String type, String prefix) throws Exception
	{
		final String fileName;
		if(type.equals(ArchiveStreamFactory.ZIP))
		{
			fileName = prefix + ".zip";
		}
		else if(type.equals(ArchiveStreamFactory.TAR))
		{
			fileName = prefix + ".tar.gz";
		}
		else
		{
			throw new IllegalArgumentException(type);
		}

		FilePath child = myTargetDir.child(fileName);

		final OutputStream outputStream = child.write();
		if(type.equals(ArchiveStreamFactory.TAR))
		{
			return new GzipCompressorOutputStream(outputStream);
		}
		return outputStream;
	}
}
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

import com.Ostermiller.util.BinaryDataException;
import com.Ostermiller.util.LineEnds;
import hudson.FilePath;
import jakarta.annotation.Nonnull;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * @author VISTALL
 * @since 22/05/2023
 */
public class LocalBundledJRE extends BundledJRE<FilePath>
{
	private final FilePath myCurrentPath;

	public LocalBundledJRE(int buildNumber, ArchiveStreamFactory archiveStreamFactory, FilePath jdkArchivePath, boolean isMac, FilePath currentPath)
	{
		super(buildNumber, archiveStreamFactory, jdkArchivePath, isMac);
		myCurrentPath = currentPath;
	}

	@Nonnull
	@Override
	protected ArchiveEntryWrapper<FilePath> createEntry(String name, ArchiveEntry tempEntry)
	{
		FilePath parent = myCurrentPath.child(name);
		return new FileArchiveEntryWrapper(parent);
	}

	@Override
	protected void copyEntry(ArchiveInputStream ais, ArchiveEntry tempEntry, ArchiveEntryWrapper<? extends FilePath> newEntry) throws IOException
	{
		FilePath item = newEntry.getItem();
		if(!tempEntry.isDirectory())
		{
			try
			{
				item.getParent().mkdirs();
			}
			catch(InterruptedException e)
			{
				throw new RuntimeException(e);
			}

			byte[] data;
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

			try (OutputStream stream = newEntry.getItem().write())
			{
				stream.write(data);
			}
			catch(InterruptedException e)
			{
				throw new RuntimeException(e);
			}
		}
		else
		{
			try
			{
				item.mkdirs();
			}
			catch(InterruptedException e)
			{
				throw new RuntimeException(e);
			}
		}
	}
}

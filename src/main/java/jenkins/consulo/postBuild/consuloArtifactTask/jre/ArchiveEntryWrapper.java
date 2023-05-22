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

package jenkins.consulo.postBuild.consuloArtifactTask.jre;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;

/**
 * @author VISTALL
 * @since 17-Jul-16
 */
public abstract class ArchiveEntryWrapper<E>
{
	public static class Tar extends ArchiveEntryWrapper<TarArchiveEntry>
	{
		public Tar(String name, ArchiveEntry tempEntry)
		{
			super(create(name, tempEntry));
		}

		private static TarArchiveEntry create(String name, ArchiveEntry originalEntry)
		{
			if(originalEntry instanceof TarArchiveEntry)
			{
				byte flags = 0;
				if(originalEntry.isDirectory())
				{
					flags = TarConstants.LF_DIR;
				}
				else if(((TarArchiveEntry) originalEntry).isSymbolicLink())
				{
					flags = TarConstants.LF_SYMLINK;
				}
				else if(((TarArchiveEntry) originalEntry).isLink())
				{
					flags = TarConstants.LF_LINK;
				}
				else
				{
					flags = TarConstants.LF_NORMAL;
				}

				String link = ((TarArchiveEntry) originalEntry).getLinkName();
				final TarArchiveEntry entry = new TarArchiveEntry(name, flags);
				if(link != null)
				{
					entry.setLinkName(link);
				}
				return entry;
			}
			else if(originalEntry instanceof CpioArchiveEntry)
			{
				byte flags = 0;
				if(originalEntry.isDirectory())
				{
					flags = TarConstants.LF_DIR;
				}
				else if(((CpioArchiveEntry) originalEntry).isSymbolicLink())
				{
					flags = TarConstants.LF_SYMLINK;
				}
//				else if(((CpioArchiveEntry) originalEntry).isLink())
//				{
//					flags = TarConstants.LF_LINK;
//				}
				else
				{
					flags = TarConstants.LF_NORMAL;
				}

//				String link = ((CpioArchiveEntry) originalEntry).getLinkName();
				final TarArchiveEntry entry = new TarArchiveEntry(name, flags);
//				if(link != null)
//				{
//					entry.setLinkName(link);
//				}
				return entry;
			}
			return new TarArchiveEntry(name);
		}

		@Override
		public boolean isDirectory()
		{
			return myItem.isDirectory();
		}

		@Override
		public void setTime(long date)
		{
			myItem.setModTime(date);
		}

		@Override
		public void setMode(int mode)
		{
			myItem.setMode(mode);
		}

		@Override
		public void setSize(long size)
		{
			myItem.setSize(size);
		}
	}

	public static class Zip extends ArchiveEntryWrapper<ZipArchiveEntry>
	{
		public Zip(String name)
		{
			super(new ZipArchiveEntry(name));
		}

		@Override
		public boolean isDirectory()
		{
			return myItem.isDirectory();
		}

		@Override
		public void setTime(long date)
		{
			myItem.setTime(date);
		}

		@Override
		public void setMode(int mode)
		{
		}

		@Override
		public void setSize(long size)
		{
			myItem.setSize(size);
		}
	}

	protected final E myItem;

	protected ArchiveEntryWrapper(E archiveEntry)
	{
		myItem = archiveEntry;
	}

	public E getItem()
	{
		return myItem;
	}

	public abstract boolean isDirectory();

	public abstract void setTime(long date);

	public abstract void setMode(int mode);

	public abstract void setSize(long size);
}
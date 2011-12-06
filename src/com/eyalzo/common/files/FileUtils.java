/** 
Copyright 2011 Eyal Zohar. All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are
permitted provided that the following conditions are met:

   1. Redistributions of source code must retain the above copyright notice, this list of
      conditions and the following disclaimer.

   2. Redistributions in binary form must reproduce the above copyright notice, this list
      of conditions and the following disclaimer in the documentation and/or other materials
      provided with the distribution.

THIS SOFTWARE IS PROVIDED BY EYAL ZOHAR ''AS IS'' AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL EYAL ZOHAR OR
CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

The views and conclusions contained in the software and documentation are those of the
authors and should not be interpreted as representing official policies, either expressed
or implied, of Eyal Zohar.
 */

package com.eyalzo.common.files;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Read and write methods to data files.
 * 
 * @author Eyal Zohar
 */
public class FileUtils
{
	/**
	 * Write received block to physical file on disk.
	 * 
	 * @param fileName
	 *            Full path of existing file on disk.
	 * @param startOffset
	 *            0-based start position in file, inclusive.
	 * @param buffer
	 *            Buffer holding the block itself, from position(), meaning that
	 *            its remaining() is the length of data to write.
	 * @param log
	 * @return False if file does not exist or failed to write all bytes from
	 *         any other reason.
	 */
	public static boolean writeBlock(String fileName, long startOffset, ByteBuffer buffer, Logger log)
	{
		RandomAccessFile randomAccessFile = null;
		File file = new File(fileName);

		//
		// File is supposed to be on disk already, since the file was used for
		// the first time
		//
		if (!file.exists())
		{
			if (log != null)
				log.log(Level.WARNING, "Cannot write file \"" + fileName + "\" that does not exist");
			return false;
		}

		//
		// Make sure that position and length match the file size
		//
		if (startOffset + buffer.remaining() > file.length())
		{
			if (log != null)
				log.log(Level.SEVERE,
						"Wrong position and/or length when trying to write file \"" + fileName + "\", file length "
								+ file.length() + ", offset " + startOffset + ", data length " + buffer.remaining());
			return false;
		}

		//
		// Create the file descriptor
		//
		try
		{
			// It can fail if file does not exist or there is a security problem
			randomAccessFile = new RandomAccessFile(file, "rw");
		} catch (Exception e)
		{
			if (log != null)
				log.log(Level.WARNING, "Failed to open file \"" + fileName + "\" for write:\n" + e);
			return false;
		}

		//
		// Move writing head to the right position
		//
		try
		{
			randomAccessFile.seek(startOffset);
		} catch (Exception e)
		{
			if (log != null)
				log.log(Level.SEVERE, "Failed to set position when trying to write file \"" + fileName
						+ "\", file length " + file.length() + ", offset " + startOffset + ":\n{3}" + e);
			try
			{
				randomAccessFile.close();
			} catch (Exception e2)
			{
			}
			return false;
		}

		WritableByteChannel channel = randomAccessFile.getChannel();

		//
		// Write the data
		//
		int dataLength = buffer.remaining();
		int writtenBytes = 0;
		try
		{
			writtenBytes = channel.write(buffer);
		} catch (IOException e)
		{
			if (log != null)
				log.log(Level.SEVERE, "Failed to write data to file \"" + fileName + "\", file length " + file.length()
						+ ", offset " + startOffset + ", data length " + dataLength + ":\n" + e);
			try
			{
				randomAccessFile.close();
			} catch (Exception e2)
			{
			}
			return false;
		}

		//
		// Close the file
		//
		try
		{
			randomAccessFile.close();
		} catch (Exception e2)
		{
		}

		//
		// Check if all bytes were written
		//
		if (writtenBytes != dataLength)
		{
			if (log != null)
				log.log(Level.SEVERE, "Failed to write data to file \"" + fileName + "\", wrote only " + writtenBytes
						+ " instead of " + dataLength);
			return false;
		}

		return true;
	}

	/**
	 * Read one block from data file.
	 * 
	 * @param fileName
	 * @param startOffset
	 *            0-based start position in file, inclusive.
	 * @param length
	 * @param log
	 * @return Null if failed to read form some reason.
	 */
	public static ByteBuffer readBlock(String fileName, long startOffset, int length, ByteOrder byteOrder, Logger log)
	{
		File file = new File(fileName);

		//
		// File is supposed to be on disk
		//
		if (!file.exists())
		{
			if (log != null)
				log.log(Level.WARNING, "Cannot read file {0}, that does not exist", fileName);
			return null;
		}

		//
		// Make sure that position and length match the file size
		//
		if (startOffset + length > file.length())
		{
			if (log != null)
				log.log(Level.SEVERE, "Wrong position and/or length when trying to read file \"" + fileName
						+ "\", file length " + file.length() + ", offset " + startOffset + ", data length " + length);
			return null;
		}

		RandomAccessFile randomAccessFile;
		//
		// Create the file descriptor
		//
		try
		{
			// It can fail if file does not exist or there is a security problem
			randomAccessFile = new RandomAccessFile(file, "rw");
		} catch (Exception e)
		{
			if (log != null)
				log.log(Level.WARNING, "Failed to open file \"" + fileName + "\" for read:\n" + e);
			return null;
		}

		//
		// Move writing head to the right position
		//
		try
		{
			randomAccessFile.seek(startOffset);
		} catch (Exception e)
		{
			if (log != null)
				log.log(Level.SEVERE, "Failed to set position when trying to read file \"" + fileName
						+ "\", file length " + file.length() + ", offset " + startOffset + ":\n" + e);
			try
			{
				randomAccessFile.close();
			} catch (Exception e2)
			{
			}
			return null;
		}

		// Allocate new buffer
		ByteBuffer buffer = ByteBuffer.allocate(length).order(byteOrder);

		ReadableByteChannel channel = randomAccessFile.getChannel();

		//
		// Read the data
		//
		int readBytes = 0;
		try
		{
			readBytes = channel.read(buffer);
		} catch (IOException e)
		{
			if (log != null)
				log.log(Level.SEVERE, "Failed to read data from \"" + fileName + "\", file length " + file.length()
						+ ", offset " + startOffset + ", data length " + length + ":\n" + e);
			try
			{
				randomAccessFile.close();
			} catch (Exception e2)
			{
			}
			return null;
		}

		//
		// Close the file
		//
		try
		{
			randomAccessFile.close();
		} catch (Exception e2)
		{
		}

		//
		// Check if all bytes were written
		//
		if (readBytes != length)
		{
			if (log != null)
				log.log(Level.SEVERE, "Failed to read data from \"" + fileName + "\", read only " + readBytes
						+ " instead of " + length);
			return null;
		}

		buffer.flip();

		return buffer;
	}

	/**
	 * Read one block from data file.
	 * 
	 * @param buffer
	 *            Buffer ready for reading from position to limit. Need to set
	 *            the limit before call. On successful return it has the
	 *            position and limit ready for reading.
	 * @param fileName
	 * @param startOffset
	 *            0-based start position in file, inclusive.
	 * @param log
	 *            Optional.
	 * @return Number of read bytes or 0 on error: missing file, end of file,
	 *         buffer is not large enough, etc.
	 */
	public static int readBlock(ByteBuffer buffer, String fileName, long startOffset, Logger log)
	{
		File file = new File(fileName);

		//
		// File is supposed to be on disk
		//
		if (!file.exists())
		{
			if (log != null)
				log.log(Level.WARNING, "Cannot read file {0}, that does not exist", fileName);
			return 0;
		}

		//
		// Make sure that position and length match the file size
		//
		if (startOffset + buffer.remaining() > file.length())
		{
			int length = (int) (file.length() - startOffset);
			if (length <= 0)
			{
				if (log != null)
					log.log(Level.SEVERE, "Wrong position and/or length when trying to read file \"" + fileName
							+ "\", file length " + file.length() + ", offset " + startOffset + ", data length "
							+ length);
				return 0;
			}

			buffer.limit(buffer.position() + length);
		}

		RandomAccessFile randomAccessFile;
		//
		// Create the file descriptor
		//
		try
		{
			// It can fail if file does not exist or there is a security problem
			randomAccessFile = new RandomAccessFile(file, "rw");
		} catch (Exception e)
		{
			if (log != null)
				log.log(Level.WARNING, "Failed to open file \"" + fileName + "\" for read:\n" + e);
			return 0;
		}

		//
		// Move writing head to the right position
		//
		try
		{
			randomAccessFile.seek(startOffset);
		} catch (Exception e)
		{
			if (log != null)
				log.log(Level.SEVERE, "Failed to set position when trying to read file \"" + fileName
						+ "\", file length " + file.length() + ", offset " + startOffset + ":\n" + e);
			try
			{
				randomAccessFile.close();
			} catch (Exception e2)
			{
			}
			return 0;
		}

		ReadableByteChannel channel = randomAccessFile.getChannel();

		//
		// Read the data
		//
		int readBytes = 0;
		try
		{
			readBytes = channel.read(buffer);
		} catch (IOException e)
		{
			if (log != null)
				log.log(Level.SEVERE, "Failed to read data from \"" + fileName + "\", file length " + file.length()
						+ ", offset " + startOffset + ", data length " + buffer.remaining() + ":\n" + e);
			try
			{
				randomAccessFile.close();
			} catch (Exception e2)
			{
			}
			return 0;
		}

		//
		// Close the file
		//
		try
		{
			randomAccessFile.close();
		} catch (Exception e2)
		{
		}

		buffer.limit(buffer.position());
		buffer.position(buffer.position() - readBytes);

		return readBytes;
	}

	/**
	 * Set data filename, and create it if needed and asked to.
	 * 
	 * @param downloadsDirName
	 * @return True if file already exists.
	 */
	public static boolean initDataFile(String dirName, String fileName, long fileSize, boolean createFileIfNotExists)
	{
		String dataFileName = dirName + File.separatorChar + fileName;
		return initDataFile(dataFileName, fileSize, createFileIfNotExists);
	}

	public static boolean initDataFile(String dataFileName, long fileSize, boolean createFileIfNotExists)
	{
		File file = new File(dataFileName);

		//
		// If file already exists we can safely return now
		//
		if (file.exists())
		{
			return true;
		}

		if (createFileIfNotExists)
		{
			return createDataFile(file, fileSize);
		}
		return false;
	}

	private static boolean createDataFile(File file, long fileSize)
	{
		RandomAccessFile randomAccessFile = null;
		//
		// Try to create the new file
		//
		try
		{
			file.createNewFile();
			randomAccessFile = new RandomAccessFile(file, "rw");
		} catch (IOException e)
		{
			return false;
		}

		//
		// Set the file size
		//
		try
		{
			randomAccessFile.setLength(fileSize);
		} catch (Exception e)
		{
			try
			{
				randomAccessFile.close();
			} catch (Exception e1)
			{
			}
			return false;
		}

		try
		{
			randomAccessFile.close();
		} catch (Exception e1)
		{
		}
		return true; // File was created successfully
	}

	/**
	 * Creates the directory if does not exist
	 * 
	 * @param dirName
	 *            The directory to check and create if needed
	 * @param log
	 *            Logger for messages, null is also an option
	 * @return True if the directory existed or successfully created, otherwise
	 *         False
	 */
	public static boolean createNonExsistingDir(String dirName, Logger log)
	{
		File newFolder = new File(dirName);
		//
		// Even though the first thing that mkdirs() does is check the same,
		// i add this condition for debug
		//
		if (newFolder.exists())
		{
			if (log != null)
				log.log(Level.WARNING, "Dir {0} already exists", dirName);
			return true;
		}

		try
		{
			newFolder.mkdirs();
			if (log != null)
				log.log(Level.WARNING, "Created dir {0} and all its sub-dirs", dirName);
			return true;
		} catch (Exception e)
		{
			if (log != null && log.isLoggable(Level.WARNING))
			{
				if (log != null)
					log.log(Level.WARNING, "Error when trying to create directory \"" + dirName + "\":\n" + e);
			}
			return false;
		}
	}

	public static long getFileSize(String fileName, Logger log)
	{
		File file = new File(fileName);

		//
		// File is supposed to be on disk already, since the file was used for
		// the first time
		//
		if (!file.exists())
		{
			if (log != null)
				log.log(Level.WARNING, "File \"" + fileName + "\" not found, so size is zero");
			return 0;
		}

		return file.length();
	}

	public static List<String> getDirFileListSorted(String dirName, long minFileSize, long maxFileSize)
	{
		// Sanity check
		if (dirName == null)
			return null;

		// Make sure that this is an existing directory
		File file = new File(dirName);
		if (!file.isDirectory())
			return null;

		dirName = file.getAbsolutePath();
		
		// Get all names
		LinkedList<String> list = new LinkedList<String>();
		String[] fileNames = file.list();

		// This should not happen after we already verified that the directory
		// exists
		if (fileNames == null)
			return null;

		for (String curName : fileNames)
		{
			String fullPath = dirName + File.separatorChar + curName;

			// Make sure that the entry is for an existing file
			File curFile = new File(fullPath);
			if (curFile == null || !curFile.isFile())
				continue;

			// File size
			if (curFile.length() < minFileSize || curFile.length() > maxFileSize)
				continue;

			// Add full path to list
			list.add(fullPath);
		}

		// Sort the list (slower with full path, but...)
		Collections.sort(list);

		return list;
	}

}

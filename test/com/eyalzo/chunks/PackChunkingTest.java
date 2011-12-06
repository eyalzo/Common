/**
 * Copyright 2011 Eyal Zohar. All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 * following disclaimer in the documentation and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY EYAL ZOHAR ''AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * EYAL ZOHAR OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 * The views and conclusions contained in the software and documentation are those of the authors and should not be
 * interpreted as representing official policies, either expressed or implied, of Eyal Zohar.
 */
package com.eyalzo.chunks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.LinkedList;
import java.util.Random;

import org.junit.Test;

import com.eyalzo.common.chunks.PackChunking;

/**
 * @author Eyal Zohar
 */
public class PackChunkingTest
{
	private Random	rnd	= new Random(System.currentTimeMillis());

	/**
	 * Count anchors in random data and compare with the expected number of anchors +-15%. Also a speed test.
	 * 
	 * Test method for {@link com.eyalzo.common.chunks.PackChunking#anchorCount(byte[])}.
	 */
	@Test
	public void testAnchorCountByteArray()
	{
		// Create a large buffer (for speed) and fill it with random bytes
		byte[] buffer = new byte[20000000];
		rnd.nextBytes(buffer);

		System.out.println("Anchor count\n============\n"
				+ "Mask-bits Expected-anchors Found-anchors Expected-chunk-size "
				+ "Found-chunk-size Speed-Mbps Anchors-diff");

		// Try all the possible mask lengths
		for (int maskBits = PackChunking.MIN_MASK_BITS; maskBits <= PackChunking.MAX_MASK_BITS; maskBits++)
		{
			PackChunking pack = new PackChunking(maskBits);

			// Find anchors
			long timeBefore = System.currentTimeMillis();
			int anchorCount = pack.anchorCount(buffer);
			long interval = System.currentTimeMillis() - timeBefore + 1;
			int chunkSize = (buffer.length - PackChunking.WINDOW_BYTES_LEN + 1) / anchorCount;

			// Expected
			int expectedAnchorCount = pack.expectedAnchorCount(buffer.length);
			int expectedChunkSize = (buffer.length - PackChunking.WINDOW_BYTES_LEN + 1) / expectedAnchorCount;

			// Print
			double diff = 100.0 * anchorCount / expectedAnchorCount - 100;
			System.out.println(String.format("%,9d %,16d %,13d %,19d %,15d %,11d %11.1f%%", maskBits,
					expectedAnchorCount, anchorCount, expectedChunkSize, chunkSize, buffer.length * 8L / interval
							/ 1000, diff));

			// Assert at 15%
			assertTrue("Too many/little anchors", Math.abs(diff) < 15);
		}
	}

	/**
	 * Count chunks in random data and compare with the expected number of chunks +-15%. Also a speed test.
	 * 
	 * Test method for {@link com.eyalzo.common.chunks.PackChunking#getChunks(java.util.List, byte[], int, int)} .
	 */
	@Test
	public void testGetChunksRandom()
	{
		// Create a large buffer (for speed) and fill it with random bytes
		byte[] buffer = new byte[5000000];
		rnd.nextBytes(buffer);
		testGetChunks(buffer);
	}

	private void testGetChunks(byte[] buffer)
	{
		System.out.println("\nChunk count\n===========\n"
				+ "Mask-bits Min-chunk Max-chunk Expected-chunks Found-chunks Expected-chunk-size "
				+ "Found-chunk-size Speed-Mbps Chunks-diff");

		// Try all the possible mask lengths
		for (int maskBits = PackChunking.MIN_MASK_BITS; maskBits <= PackChunking.MAX_MASK_BITS; maskBits++)
		{
			PackChunking pack = new PackChunking(maskBits);

			// Get chunks
			long timeBefore = System.currentTimeMillis();
			LinkedList<Long> chunkList = new LinkedList<Long>();
			int nextOffset = pack.getChunks(chunkList, buffer, 0, buffer.length, true);
			long interval = System.currentTimeMillis() - timeBefore + 1;

			int chunkSize = chunkList.isEmpty() ? buffer.length : (nextOffset / chunkList.size());

			// Expected
			int expectedChunkCount = pack.expectedChunkCount(buffer.length);
			int expectedChunkSize = expectedChunkCount == 0 ? buffer.length : (buffer.length
					- PackChunking.WINDOW_BYTES_LEN + 1)
					/ expectedChunkCount;

			// Print
			double diff = 100.0 * chunkList.size() / expectedChunkCount - 100;
			System.out.println(String.format("%,9d %,9d %,9d %,15d %,12d %,19d %,16d %,10d %10.1f%%", maskBits,
					pack.getMinChunkSize(), pack.getMaxChunkSize(), expectedChunkCount, chunkList.size(),
					expectedChunkSize, chunkSize, buffer.length * 8L / interval / 1000, diff));

			// Print chunk list
			// PackChunking.printChunkList(chunkList);

			// Sum up the chunks
			long totalSize = 0;
			for (long curChunk : chunkList)
			{
				totalSize += PackChunking.chunkToLen(curChunk);
			}

			// If sum of chunks is smaller than buffer, but because the last chunk was too small
			if (totalSize < buffer.length && totalSize + pack.getMinChunkSize() > buffer.length)
				;
			else
				// Compare sum
				assertEquals("Total buffer size differ from sum of chunks", buffer.length, totalSize);

			// Assert at 15%
			if (expectedChunkCount >= 20)
				assertTrue("Too many/little chunks", Math.abs(diff) < 15);
		}
	}
}

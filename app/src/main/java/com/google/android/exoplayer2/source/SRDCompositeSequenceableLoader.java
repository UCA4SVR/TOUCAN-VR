/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.source;

import com.google.android.exoplayer2.C;

import fr.unice.i3s.uca4svr.toucan_vr.mediaplayer.source.chunk.OurChunkSampleStream;
import fr.unice.i3s.uca4svr.toucan_vr.tilespicker.TilesPicker;

/**
 * A {@link SequenceableLoader} that encapsulates multiple other {@link SequenceableLoader}s.
 */
public final class SRDCompositeSequenceableLoader implements SequenceableLoader {

	private final SequenceableLoader[] loaders;
	private boolean imReplacing;

	public SRDCompositeSequenceableLoader(SequenceableLoader[] loaders) {
		this.loaders = loaders;
		this.imReplacing = false;
	}

	@Override
	public long getNextLoadPositionUs() {
		long nextLoadPositionUs = Long.MAX_VALUE;
		for (SequenceableLoader loader : loaders) {
			long loaderNextLoadPositionUs = loader.getNextLoadPositionUs();
			if (loaderNextLoadPositionUs != C.TIME_END_OF_SOURCE) {
				nextLoadPositionUs = Math.min(nextLoadPositionUs, loaderNextLoadPositionUs);
			}
		}
		return nextLoadPositionUs == Long.MAX_VALUE ? C.TIME_END_OF_SOURCE : nextLoadPositionUs;
	}

	@Override
	public boolean continueLoading(long positionUs) {
	/* I need to buffer again: if I was replacing I have to stop the process */
		if (this.imReplacing) {
			for (SequenceableLoader loader : loaders) {
				((OurChunkSampleStream) loader).stopReplacing();
			}
			this.imReplacing = false;
		}
		boolean madeProgress = false;
		boolean madeProgressThisIteration;
		do {
			madeProgressThisIteration = false;
			long nextLoadPositionUs = getNextLoadPositionUs();
			if (nextLoadPositionUs == C.TIME_END_OF_SOURCE) {
				break;
			}
			for (SequenceableLoader loader : loaders) {
				if (loader.getNextLoadPositionUs() == nextLoadPositionUs) {
					madeProgressThisIteration |= loader.continueLoading(positionUs);
				}
			}
			madeProgress |= madeProgressThisIteration;
		} while (madeProgressThisIteration);
		return madeProgress;
	}

	public boolean replaceChunks(long playbackPosition) {
		this.imReplacing = true;
		boolean madeProgress = false;
		TilesPicker tilesPicker = TilesPicker.getPicker();
		//Need to replace: which one? choose the currently picked tiles!
		for (SequenceableLoader loader : loaders) {
			if (tilesPicker.isPicked(((OurChunkSampleStream) loader).adaptationSetIndex)) {
				madeProgress |= ((OurChunkSampleStream) loader).replace(playbackPosition);
			}
		}
		return madeProgress;
	}
}
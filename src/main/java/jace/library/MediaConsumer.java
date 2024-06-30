/** 
* Copyright 2024 Brendan Robert
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
**/
package jace.library;

import java.io.IOException;
import java.util.Optional;

import jace.library.MediaEntry.MediaFile;
import javafx.scene.control.Label;

/**
 *
 * @author brobert
 */
public interface MediaConsumer {
    Optional<Label> getIcon();
    void setIcon(Optional<Label> i);
    void insertMedia(MediaEntry e, MediaFile f) throws IOException;
    MediaEntry getMediaEntry();
    MediaFile getMediaFile();
    boolean isAccepted(MediaEntry e, MediaFile f);
    void eject();
}

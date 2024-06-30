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

package jace.state;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates an object that possesses state which should be preserved Stateful
 * variables are indicated by members annotated by Stateful This is used for
 * saved states and rewind features. The StateManager uses this annotation to
 * identify where to preserve and restore state as needed.
 *
 * Configurable fields are inherently stateful and do not necessarily have to be
 * indicated. Configuration is captured as part of state.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Stateful {
}

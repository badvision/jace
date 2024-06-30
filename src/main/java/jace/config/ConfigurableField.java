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

package jace.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A configurable field annotation means that an object property can be changed
 * by the end-user.
 * NOTE: Any field that implements this must be public and serializable!
 * If a field is not serializable, it will result in a serialization error
 * when the configuration is being saved.  There is no way to offer a compiler
 * warning to avoid this, unfortunately.
 * One way you can work with this constraint when allowing large reconfiguration
 * of functionality, such as Cards or other class implementations of hardware,
 * is to store the class itself of the component as a configuration value
 * and let the Reconfigure method generate a new instance.
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ConfigurableField {
    String name();
    String shortName() default "";
    String defaultValue() default "";
    String description() default "";
    String category() default "General";
    boolean enablesDevice() default false;
}
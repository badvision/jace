/*
 * Copyright (C) 2012 Brendan Robert (BLuRry) brendan.robert@gmail.com.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
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
    public String name();
    public String shortName() default "";
    public String defaultValue() default "";
    public String description() default "";
    public String category() default "General";
}
/*
 * Copyright 2016 Brendan Robert
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
package jace.ide;

import java.util.Collections;
import java.util.Map;

/**
 * This is a program that is intended to be defined and executed outside of a IDE session
 * @author blurry
 */
public class HeadlessProgram extends Program {    
    public HeadlessProgram(DocumentType type) {
        super(type, Collections.EMPTY_MAP);
    }

    String program;
    @Override
    public String getValue() {
        return program;
    }

    @Override
    public void setValue(String value) {
        program = value;
    }

    public HeadlessProgram() {
        super(null, null);
    }

    CompileResult lastResult = null;
    @Override
    protected void manageCompileResult(CompileResult lastResult) {
        this.lastResult = lastResult;
    }
}

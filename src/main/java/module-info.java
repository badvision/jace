/*
 * Copyright 2023 org.badvision.
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

@SuppressWarnings("all")
module jace {
    requires nestedvm;
    requires java.base;
    requires java.logging;
    requires transitive java.desktop;
    requires java.datatransfer;
    requires java.scripting;
    requires static transitive java.compiler;
    requires javafx.fxmlEmpty;
    requires javafx.fxml;
    requires javafx.swing;
    requires javafx.controlsEmpty;
    requires transitive javafx.controls;
    requires javafx.baseEmpty;
    requires javafx.base;
    requires javafx.webEmpty;
    requires javafx.web;
    requires javafx.graphicsEmpty;
    requires javafx.graphics;
    requires javafx.mediaEmpty;
    requires javafx.media;
    requires jdk.jsobject;
    requires org.lwjgl.openal;
    requires org.lwjgl.stb;
    requires org.lwjgl.glfw;
    requires org.lwjgl;
    
    // requires org.reflections;
    
    opens jace to javafx.graphics, javafx.fxml, javafx.controls;
    opens jace.config to javafx.fxml, javafx.controls;
    opens jace.ui to javafx.graphics, javafx.fxml, javafx.controls;
    // opens jace.data to javafx.graphics, javafx.fxml, javafx.controls;
    opens jace.ide to javafx.graphics, javafx.fxml, javafx.controls;
    // opens fxml to javafx.graphics, javafx.fxml, javafx.controls;
    // opens styles to javafx.graphics, javafx.fxml, javafx.controls;
    
    uses javax.sound.sampled.SourceDataLine;

    provides javax.annotation.processing.Processor with jace.config.InvokableActionAnnotationProcessor;

    exports jace;
    exports jace.apple2e;
    exports jace.cheat;
    exports jace.config;
    exports jace.core;
    exports jace.hardware;
    exports jace.hardware.mockingboard;
    exports jace.library;
    exports jace.state;
    exports jace.ui;

}

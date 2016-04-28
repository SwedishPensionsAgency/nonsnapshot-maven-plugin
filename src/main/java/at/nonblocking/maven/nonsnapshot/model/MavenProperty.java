/*
 * Copyright 2012-2013 the original author or authors.
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
package at.nonblocking.maven.nonsnapshot.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a Maven property in module
 *
 * @author Jörgen Andersson
 */
public class MavenProperty {
  
  private final String name;
  
  private final int propertyLocation;
  
  private final List<MavenModuleDependency> propertyReferencingModuleDependency = new ArrayList<>();

  public MavenProperty(String name, int propertyLocation) {
    this.name = name;
    this.propertyLocation = propertyLocation;
  }

  public String getName() {
    return name;
  }

  public int getPropertyLocation() {
    return propertyLocation;
  }

  public List<MavenModuleDependency> getPropertyReferencingModuleDependency() {
    return propertyReferencingModuleDependency;
  }
}
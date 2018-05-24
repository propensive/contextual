/*
  
  Contextual, version 1.1.0. Copyright 2018 Jon Pretty, Propensive Ltd.

  The primary distribution site is: https://propensive.com/

  Licensed under the Apache License, Version 2.0 (the "License"); you may not use
  this file except in compliance with the License. You may obtain a copy of the
  License at
  
      http://www.apache.org/licenses/LICENSE-2.0
 
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
  License for the specific language governing permissions and limitations under
  the License.

*/
package contextual

/** A [[Context]] describes the nature of the position in an interpolated string where a
  * substitution is made, and determines how values of a particular type should be interpreted
  * in the given position. */
trait Context {
  /** A string representation which is meaningful for a singleton-object [[Context]] instance,
    * calculated by reflecting on its class name. */
  override def toString: String = getClass.getName.split("\\.").last.dropRight(1)
}

/*
 * Copyright (c) 2017-2020 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.graal.squeak.nodes;

import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.nodes.Node;

import de.hpi.swa.graal.squeak.model.BooleanObject;
import de.hpi.swa.graal.squeak.util.FrameAccess;

@ImportStatic({BooleanObject.class, FrameAccess.class, SqueakGuards.class})
@TypeSystemReference(SqueakTypes.class)
public abstract class AbstractNode extends Node {

}

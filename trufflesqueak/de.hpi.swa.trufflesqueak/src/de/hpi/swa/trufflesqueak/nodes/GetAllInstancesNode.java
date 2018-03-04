package de.hpi.swa.trufflesqueak.nodes;

import java.util.List;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

import de.hpi.swa.trufflesqueak.model.BaseSqueakObject;
import de.hpi.swa.trufflesqueak.model.CompiledCodeObject;
import de.hpi.swa.trufflesqueak.model.ObjectLayouts.PROCESS;
import de.hpi.swa.trufflesqueak.model.PointersObject;
import de.hpi.swa.trufflesqueak.nodes.process.GetActiveProcessNode;

public abstract class GetAllInstancesNode extends AbstractNodeWithCode {
    @Child private GetActiveProcessNode getActiveProcessNode;
    @Child private GetOrCreateContextNode getOrCreateContextNode;

    public static GetAllInstancesNode create(CompiledCodeObject code) {
        return GetAllInstancesNodeGen.create(code);
    }

    protected GetAllInstancesNode(CompiledCodeObject code) {
        super(code);
        getActiveProcessNode = GetActiveProcessNode.create(code.image);
        getOrCreateContextNode = GetOrCreateContextNode.create(code);
    }

    public abstract List<BaseSqueakObject> execute(VirtualFrame frame);

    @Specialization
    protected List<BaseSqueakObject> getInstancesArray(VirtualFrame frame) {
        PointersObject activeProcess = getActiveProcessNode.executeGet();
        activeProcess.atput0(PROCESS.SUSPENDED_CONTEXT, getOrCreateContextNode.executeGet(frame));
        try {
            return code.image.objects.allInstances();
        } finally {
            activeProcess.atput0(PROCESS.SUSPENDED_CONTEXT, code.image.nil);
        }
    }
}

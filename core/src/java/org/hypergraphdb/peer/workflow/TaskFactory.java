package org.hypergraphdb.peer.workflow;

import java.util.UUID;
import org.hypergraphdb.peer.HyperGraphPeer;

public interface TaskFactory
{
	TaskActivity<?> newTask(HyperGraphPeer thisPeer, UUID taskId, Object msg);
}
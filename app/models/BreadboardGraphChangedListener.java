package models;

import java.util.*;
import com.tinkerpop.blueprints.*;
import com.tinkerpop.blueprints.util.wrappers.event.listener.GraphChangedListener;
import org.codehaus.jackson.node.ObjectNode;
import play.libs.Json;
import play.Logger;

public class BreadboardGraphChangedListener implements GraphChangedListener
{
	private Graph graph;
	private ArrayList<ClientListener> adminListeners = new ArrayList<ClientListener>();
    private static HashMap<String, Client> clientListeners = new HashMap<String, Client>();

	public BreadboardGraphChangedListener(Graph graph)
	{
		this.graph = graph;
	}

	public void addAdminListener(ClientListener adminListener)
	{
		adminListeners.add(adminListener);
	}

	public ArrayList<ClientListener> getAdminListeners()
	{
		return this.adminListeners;
	}

	public ArrayList<Client> getClientListeners()
	{
		ArrayList<Client> returnArrayList = new ArrayList<Client>();
		for (Client client : clientListeners.values()) {
			returnArrayList.add(client);
		}
		return returnArrayList;
	}

	public void addClientListener(Client clientListener)
	{
		clientListeners.put(clientListener.id, clientListener);
	}

    public void removeAdminListener(ClientListener adminListener)
    {
        adminListeners.remove(adminListener);
    }

	@Override
	public void edgeAdded(Edge edge)
	{
		//Logger.debug("BreadboardGraphChangedListener edgeAdded");
		for (ClientListener al : adminListeners)
			al.edgeAdded(edge);

        clientEdgeChanged(edge);
	}

	@Override
	public void edgePropertyChanged(Edge edge, String key, Object oldValue, Object setValue)
	{
		for (ClientListener al : adminListeners)
			al.edgePropertyChanged(edge, key, setValue);

        clientEdgePropertyChanged(edge, key, setValue);
	}

	@Override
	public void edgePropertyRemoved(Edge edge, String key, Object removedValue)
	{
		for (ClientListener al : adminListeners)
			al.edgePropertyRemoved(edge, key);

        clientEdgePropertyChanged(edge, key, removedValue);
	}

	@Override
	public void edgeRemoved(Edge edge, Map<String, Object> props)
	{
		for (ClientListener al : adminListeners)
			al.edgeRemoved(edge);

        clientEdgeChanged(edge);
	}

	@Override
	public void vertexAdded(Vertex vertex)
	{
		for (ClientListener al : adminListeners)
			al.vertexAdded(vertex);

        clientVertexChanged(vertex, false);
	}

	@Override
	public void vertexPropertyChanged(Vertex vertex, String key, Object oldValue, Object setValue)
	{
		for (ClientListener al : adminListeners)
			al.vertexPropertyChanged(vertex, key, oldValue, setValue);

		boolean pvt = (key.equals("private") || key.equals("choices") || key.equals("text"));
        clientVertexChanged(vertex, pvt);
	}

	@Override
	public void vertexPropertyRemoved(Vertex vertex, String key, Object removedValue)
	{
		for (ClientListener al : adminListeners)
			al.vertexPropertyRemoved(vertex, key);

		boolean pvt = (key.equals("private") || key.equals("choices") || key.equals("text"));
        clientVertexChanged(vertex, pvt);
	}

	@Override
	public void vertexRemoved(Vertex vertex, Map<String, Object> props)
	{
		for (ClientListener al : adminListeners)
			al.vertexRemoved(vertex);

        clientVertexChanged(vertex, false);
	}

	private void notifyAdminListeners()
	{
        Logger.debug("notifyAdminListeners");
		/*
		for (AdminListener al : adminListeners)
			al.graphChanged(graph);
		*/
	}

	private void clientEdgePropertyChanged(Edge edge, String key, Object value)
	{
		// inProps are only visible by the inVertex and outProps are only visible by the outVertex
		if (key.equals("inProps")) {
			Vertex inVertex = edge.getVertex(Direction.IN);
        	String inId = (String)inVertex.getId();
        	if (clientListeners.containsKey(inId)) {
        		Client inClient = clientListeners.get(inId);
        		inClient.updateGraph(inVertex);
        	}
		} else if (key.equals("outProps")) {
			Vertex outVertex = edge.getVertex(Direction.OUT);
        	String outId = (String)outVertex.getId();
        	if (clientListeners.containsKey(outId)) {
        		Client outClient = clientListeners.get(outId);
        		outClient.updateGraph(outVertex);
        	}
		} else {
			clientEdgeChanged(edge);
        }
	}

    private void clientEdgeChanged(Edge edge)
    {
        Vertex[] vertices = getVerticesByEdge(edge);

        String id1 = (String)vertices[0].getId();
        String id2 = (String)vertices[1].getId();

        if (clientListeners.containsKey(id1)) {
            Client c1 = clientListeners.get(id1);
            //c1.vertexAdded(vertices[1]);
            //c1.edgeAdded(edge);

            c1.updateGraph(vertices[0]);
        }

        if (clientListeners.containsKey(id2)) {
            Client c2 = clientListeners.get(id2);
            //c2.vertexAdded(vertices[0]);
            //c2.edgeAdded(edge);

            c2.updateGraph(vertices[1]);
        }
    }

    private void clientVertexChanged(Vertex vertex, boolean pvt)
    {
        // The vertex itself
        String id = (String)vertex.getId();
        if (clientListeners.containsKey(id)) {
          clientListeners.get(id).updateGraph(vertex);
        }
        // And all neighbors, if it isn't a private property
        if (! pvt) {
			    for(Vertex v : vertex.getVertices(Direction.BOTH)) {
				    id = (String)v.getId();
				    if (clientListeners.containsKey(id)) {
					    clientListeners.get(id).updateGraph(v);
				    }
			    }
        }
    }

	private ArrayList<Client> getClientsByEdge(Edge edge)
	{
		ArrayList<Client> returnClientList = new ArrayList<Client>();
		String inId = (String)edge.getVertex(Direction.IN).getId();
		String outId = (String)edge.getVertex(Direction.OUT).getId();

		if (clientListeners.containsKey(inId))
			returnClientList.add(clientListeners.get(inId));

		if (clientListeners.containsKey(outId))
			returnClientList.add(clientListeners.get(outId));

		return returnClientList;
	}

	private Vertex[] getVerticesByEdge(Edge edge)
	{
		Vertex[] returnArray = new Vertex[2];
		returnArray[0] = edge.getVertex(Direction.IN);
		returnArray[1] = edge.getVertex(Direction.OUT);
		return returnArray;
	}

	private ArrayList<Client> getClientsByVertex(Vertex vertex)
	{
		ArrayList<Client> returnClientList = new ArrayList<Client>();

		// Add the client himself
		String id = (String)vertex.getId();
		if (clientListeners.containsKey(id))
			returnClientList.add(clientListeners.get(id));

        for (Vertex v : vertex.getVertices(Direction.BOTH))
        {
        	id = (String)v.getId();
        	if (clientListeners.containsKey(id))
				returnClientList.add(clientListeners.get(id));
		}
		return returnClientList;
	}

}

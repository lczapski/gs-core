/*
 * Copyright 2006 - 2011 
 *     Julien Baudry	<julien.baudry@graphstream-project.org>
 *     Antoine Dutot	<antoine.dutot@graphstream-project.org>
 *     Yoann Pigné		<yoann.pigne@graphstream-project.org>
 *     Guilhelm Savin	<guilhelm.savin@graphstream-project.org>
 * 
 * This file is part of GraphStream <http://graphstream-project.org>.
 * 
 * GraphStream is a library whose purpose is to handle static or dynamic
 * graph, create them from scratch, file or any source and display them.
 * 
 * This program is free software distributed under the terms of two licenses, the
 * CeCILL-C license that fits European law, and the GNU Lesser General Public
 * License. You can  use, modify and/ or redistribute the software under the terms
 * of the CeCILL-C license as circulated by CEA, CNRS and INRIA at the following
 * URL <http://www.cecill.info> or under the terms of the GNU LGPL as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * The fact that you are presently reading this means that you have had
 * knowledge of the CeCILL-C and LGPL licenses and that you accept their terms.
 */
package org.graphstream.stream.file;

import java.awt.Color;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Locale;

import org.graphstream.graph.Edge;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.ui.graphicGraph.GraphicEdge;
import org.graphstream.ui.graphicGraph.GraphicGraph;
import org.graphstream.ui.graphicGraph.GraphicNode;
import org.graphstream.ui.graphicGraph.StyleGroup;
import org.graphstream.ui.graphicGraph.StyleGroupSet;

/**
 * An export of a graph to PGF/TikZ format.
 * <a>http://sourceforge.net/projects/pgf/</a>
 * 
 * This allows to include graph in a latex document. Only
 * <code>writeAll(Graph,*)</code> is working, dynamics is not handle. If the
 * exported graph is a GraphicGraph, then CSS style of the graph will be used.
 * 
 * For a better rendering, it is strongly recommended to run previously a layout
 * algorithm that will add coordinates on nodes. Else, random coordinates will
 * be choosen for nodes. Layout can be run in this way : <code>
 * Graph g;
 * ...
 * SpringBox sbox = new SpringBox();
 * 
 * g.addSink(sbox);
 * sbox.addAttributeSink(g);
 * 
 * do sbox.compute(); while (sbox.getStabilization() < 0.9);
 * 
 * g.removeSink(sbox);
 * sbox.remoteAttributeSink(g);
 * </code>
 * 
 * TikZ pictures are scalable so pixel units is not handle here. The picture is
 * bounded in a box which width and height can be defined by adding attributes
 * to the graph:
 * <ul>
 * <li>"ui.tikz.width"</li>
 * <li>"ui.tikz.height"</li>
 * </ul>
 * The value of these attributes has to be considered as centimeters.
 * 
 * Common supported style :
 * <ul>
 * <li>"fill-color", alpha is supported to</li>
 * <li>"size" in "gu"</li>
 * </ul>
 * 
 * Node supported style :
 * <ul>
 * <li>"shape" with "box", "rounded-box", "circle", "triangle", "diamond"</li>
 * <li>"stroke-mode" with "plain"</li>
 * <li>"stroke-color", alpha is supported to</li>
 * <li>"stroke-width" in "gu"</li>
 * </ul>
 * 
 * Edge supported style :
 * <ul>
 * </ul>
 */
public class FileSinkTikZ extends FileSinkBase {

	/**
	 * Node attribute storing coordinates.
	 */
	public static final String XYZ_ATTR = "xyz";
	/**
	 * Graph attribute storing width of the TikZ picture.
	 */
	public static final String WIDTH_ATTR = "ui.tikz.width";
	/**
	 * Graph attribute storing height of the TikZ picture.
	 */
	public static final String HEIGHT_ATTR = "ui.tikz.height";

	protected PrintStream out;

	protected HashMap<String, String> colors = new HashMap<String, String>();
	protected HashMap<String, String> classes = new HashMap<String, String>();
	protected HashMap<String, String> classNames = new HashMap<String, String>();

	protected int classIndex = 0;
	protected int colorIndex = 0;

	protected static String formatId(String id) {
		return "node" + id.replaceAll("\\W", "_");
	}

	protected double getNodeX(Node n) {
		if (n.hasAttribute(XYZ_ATTR))
			return ((Number) (n.getArray(XYZ_ATTR)[0])).doubleValue();

		return Double.NaN;
	}

	protected double getNodeY(Node n) {
		if (n.hasAttribute(XYZ_ATTR))
			return ((Number) (n.getArray(XYZ_ATTR)[1])).doubleValue();

		return Double.NaN;
	}

	protected String checkColor(Color c) {
		String rgb = String.format(Locale.ROOT, "%.3f,%.3f,%.3f",
				c.getRed() / 255.0f, c.getGreen() / 255.0f,
				c.getBlue() / 255.0f);

		if (colors.containsKey(rgb))
			return colors.get(rgb);

		String key = String.format("tikzC%02d", colorIndex++);
		colors.put(rgb, key);

		return key;
	}

	/**
	 * Convert a StyleGroup to tikz style.
	 * 
	 * @param group
	 *            the style group to convert
	 * @return string representation of the style group usable in TikZ.
	 */
	protected String getTikzStyle(StyleGroup group) {
		StringBuilder buffer = new StringBuilder();
		LinkedList<String> style = new LinkedList<String>();

		switch (group.getType()) {
		case NODE: {
			String fill = checkColor(group.getFillColor(0));
			style.add("fill=" + fill);

			if (group.getFillColor(0).getAlpha() < 255)
				style.add(String.format(Locale.ROOT, "fill opacity=%.2f", group
						.getFillColor(0).getAlpha() / 255.0f));

			switch (group.getStrokeMode()) {
			case DOTS:
			case DASHES:
			case PLAIN:
				String stroke = checkColor(group.getStrokeColor(0));
				style.add("draw=" + stroke);
				style.add("line width="
						+ String.format("%.1fpt", group.getStrokeWidth().value));

				if (group.getStrokeColor(0).getAlpha() < 255)
					style.add(String.format(Locale.ROOT, "draw opacity=%.2f",
							group.getStrokeColor(0).getAlpha() / 255.0f));

				break;
			}

			switch (group.getShape()) {
			case CIRCLE:
				style.add("circle");
				break;
			case ROUNDED_BOX:
				style.add("rounded corners=2pt");
			case BOX:
				style.add("rectangle");
				break;
			case TRIANGLE:
				style.add("isosceles triangle");
				break;
			case DIAMOND:
				style.add("diamond");
				break;
			default:
				System.err.printf("unhandled shape : %s%n", group.getShape());
			}

			String text = checkColor(group.getTextColor(0));
			style.add("text=" + text);

			switch (group.getSize().units) {
			case GU:
				style.add("minimum size="
						+ String.format(Locale.ROOT, "%.1fcm",
								group.getSize().values.get(0)));
				break;
			default:
				System.err.printf(
						"[warning] units %s are not compatible with TikZ.%n",
						group.getSize().units);
			}

			style.add("inner sep=0pt");
		}
			break;
		case EDGE: {
			String fill = checkColor(group.getFillColor(0));
			style.add("draw=" + fill);

			if (group.getFillColor(0).getAlpha() < 255)
				style.add(String.format(Locale.ROOT, "draw opacity=%.2f", group
						.getFillColor(0).getAlpha() / 255.0f));

			switch (group.getSize().units) {
			case GU:
				style.add("line width="
						+ String.format(Locale.ROOT, "%.1fpt",
								group.getSize().values.get(0)));
				break;
			default:
				System.err.printf(
						"[warning] units %s are not compatible with TikZ.%n",
						group.getSize().units);
			}
		}
			break;
		}

		for (int i = 0; i < style.size(); i++) {
			if (i > 0)
				buffer.append(",");

			buffer.append(style.get(i));
		}

		return buffer.toString();
	}

	/*
	 * (non-Javadoc)
	 * @see org.graphstream.stream.file.FileSinkBase#exportGraph(org.graphstream.graph.Graph)
	 */
	protected void exportGraph(Graph graph) {
		double xmin, ymin, xmax, ymax;
		double width, height;
		Locale l = Locale.ROOT;

		if (graph instanceof GraphicGraph) {
			GraphicGraph gg = (GraphicGraph) graph;
			StyleGroupSet sgs = gg.getStyleGroups();

			for (StyleGroup sg : sgs.groups()) {
				String key = String.format("class%02d", classIndex++);
				classNames.put(sg.getId(), key);
				classes.put(key, getTikzStyle(sg));
			}
		}

		String nodeStyle = "circle,draw=black,fill=black";
		String edgeStyle = "draw=black";

		out.printf("%%%n%% Do not forget \\usepackage{tikz} in header.%n%%%n");

		//
		// Begin tikzpicture
		//
		out.printf("\\begin{tikzpicture}[%n");
		for (String key : classes.keySet())
			out.printf(l, "\t%s/.style={%s},%n", key, classes.get(key));
		out.printf(l, "\ttikzgsnode/.style={%s},%n", nodeStyle);
		out.printf(l, "\ttikzgsedge/.style={%s}%n", edgeStyle);
		out.printf("]%n");

		for (String rgb : colors.keySet())
			out.printf(l, "\t\\definecolor{%s}{rgb}{%s}%n", colors.get(rgb),
					rgb);

		width = graph.hasAttribute(WIDTH_ATTR) ? graph.getNumber(WIDTH_ATTR)
				: 10;
		height = graph.hasAttribute(HEIGHT_ATTR) ? graph.getNumber(HEIGHT_ATTR)
				: 10;

		xmin = ymin = Double.MAX_VALUE;
		xmax = ymax = Double.MIN_VALUE;

		for (Node n : graph.getEachNode()) {
			double x, y;

			x = getNodeX(n);
			y = getNodeY(n);

			if (!Double.isNaN(x) && !Double.isNaN(y)) {
				xmin = Math.min(xmin, x);
				xmax = Math.max(xmax, x);
				ymin = Math.min(ymin, y);
				ymax = Math.max(ymax, y);
			} else {
				System.err.printf("[warning] missing node (x,y).%n");
			}
		}

		for (Node n : graph.getEachNode()) {
			double x, y;
			String label;
			String uiClass = "tikzgsnode";

			x = getNodeX(n);
			y = getNodeY(n);

			if (Double.isNaN(x) || Double.isNaN(y)) {
				x = Math.random() * width;
				y = Math.random() * height;
			} else {
				x = width * (x - xmin) / (xmax - xmin);
				y = height * (y - ymin) / (ymax - ymin);
			}

			label = n.hasAttribute("label") ? (String) n.getLabel("label") : "";

			if (n instanceof GraphicNode)
				uiClass = classNames.get(((GraphicNode) n).style.getId());

			out.printf(l, "\t\\node[%s] (%s) at (%f,%f) {%s};%n", uiClass,
					formatId(n.getId()), x, y, label);
		}

		for (Edge e : graph.getEachEdge()) {
			String uiClass = "tikzgsedge";

			if (e instanceof GraphicEdge)
				uiClass = classNames.get(((GraphicEdge) e).style.getId());

			out.printf(l, "\t\\draw[%s] (%s) %s (%s);%n", uiClass, formatId(e
					.getSourceNode().getId()), e.isDirected() ? "->" : "--",
					formatId(e.getTargetNode().getId()));
		}

		//
		// End of tikzpicture.
		//
		out.printf("\\end{tikzpicture}%n");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.graphstream.stream.file.FileSinkBase#outputHeader()
	 */
	protected void outputHeader() throws IOException {
		if (output instanceof PrintStream)
			out = (PrintStream) output;
		else
			out = new PrintStream(output);

		colors.clear();
		classes.clear();
		classNames.clear();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.graphstream.stream.file.FileSinkBase#outputEndOfFile()
	 */
	protected void outputEndOfFile() throws IOException {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.graphstream.stream.AttributeSink#graphAttributeAdded(java.lang.String
	 * , long, java.lang.String, java.lang.Object)
	 */
	public void graphAttributeAdded(String sourceId, long timeId,
			String attribute, Object value) {
		throw new RuntimeException(FileSinkTikZ.class.getName()
				+ " does not handle dynamic.");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.graphstream.stream.AttributeSink#graphAttributeChanged(java.lang.
	 * String, long, java.lang.String, java.lang.Object, java.lang.Object)
	 */
	public void graphAttributeChanged(String sourceId, long timeId,
			String attribute, Object oldValue, Object newValue) {
		throw new RuntimeException(FileSinkTikZ.class.getName()
				+ " does not handle dynamic.");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.graphstream.stream.AttributeSink#graphAttributeRemoved(java.lang.
	 * String, long, java.lang.String)
	 */
	public void graphAttributeRemoved(String sourceId, long timeId,
			String attribute) {
		throw new RuntimeException(FileSinkTikZ.class.getName()
				+ " does not handle dynamic.");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.graphstream.stream.AttributeSink#nodeAttributeAdded(java.lang.String,
	 * long, java.lang.String, java.lang.String, java.lang.Object)
	 */
	public void nodeAttributeAdded(String sourceId, long timeId, String nodeId,
			String attribute, Object value) {
		throw new RuntimeException(FileSinkTikZ.class.getName()
				+ " does not handle dynamic.");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.graphstream.stream.AttributeSink#nodeAttributeChanged(java.lang.String
	 * , long, java.lang.String, java.lang.String, java.lang.Object,
	 * java.lang.Object)
	 */
	public void nodeAttributeChanged(String sourceId, long timeId,
			String nodeId, String attribute, Object oldValue, Object newValue) {
		throw new RuntimeException(FileSinkTikZ.class.getName()
				+ " does not handle dynamic.");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.graphstream.stream.AttributeSink#nodeAttributeRemoved(java.lang.String
	 * , long, java.lang.String, java.lang.String)
	 */
	public void nodeAttributeRemoved(String sourceId, long timeId,
			String nodeId, String attribute) {
		throw new RuntimeException(FileSinkTikZ.class.getName()
				+ " does not handle dynamic.");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.graphstream.stream.AttributeSink#edgeAttributeAdded(java.lang.String,
	 * long, java.lang.String, java.lang.String, java.lang.Object)
	 */
	public void edgeAttributeAdded(String sourceId, long timeId, String edgeId,
			String attribute, Object value) {
		throw new RuntimeException(FileSinkTikZ.class.getName()
				+ " does not handle dynamic.");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.graphstream.stream.AttributeSink#edgeAttributeChanged(java.lang.String
	 * , long, java.lang.String, java.lang.String, java.lang.Object,
	 * java.lang.Object)
	 */
	public void edgeAttributeChanged(String sourceId, long timeId,
			String edgeId, String attribute, Object oldValue, Object newValue) {
		throw new RuntimeException(FileSinkTikZ.class.getName()
				+ " does not handle dynamic.");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.graphstream.stream.AttributeSink#edgeAttributeRemoved(java.lang.String
	 * , long, java.lang.String, java.lang.String)
	 */
	public void edgeAttributeRemoved(String sourceId, long timeId,
			String edgeId, String attribute) {
		throw new RuntimeException(FileSinkTikZ.class.getName()
				+ " does not handle dynamic.");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.graphstream.stream.ElementSink#nodeAdded(java.lang.String, long,
	 * java.lang.String)
	 */
	public void nodeAdded(String sourceId, long timeId, String nodeId) {
		throw new RuntimeException(FileSinkTikZ.class.getName()
				+ " does not handle dynamic.");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.graphstream.stream.ElementSink#nodeRemoved(java.lang.String,
	 * long, java.lang.String)
	 */
	public void nodeRemoved(String sourceId, long timeId, String nodeId) {
		throw new RuntimeException(FileSinkTikZ.class.getName()
				+ " does not handle dynamic.");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.graphstream.stream.ElementSink#edgeAdded(java.lang.String, long,
	 * java.lang.String, java.lang.String, java.lang.String, boolean)
	 */
	public void edgeAdded(String sourceId, long timeId, String edgeId,
			String fromNodeId, String toNodeId, boolean directed) {
		throw new RuntimeException(FileSinkTikZ.class.getName()
				+ " does not handle dynamic.");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.graphstream.stream.ElementSink#edgeRemoved(java.lang.String,
	 * long, java.lang.String)
	 */
	public void edgeRemoved(String sourceId, long timeId, String edgeId) {
		throw new RuntimeException(FileSinkTikZ.class.getName()
				+ " does not handle dynamic.");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.graphstream.stream.ElementSink#graphCleared(java.lang.String,
	 * long)
	 */
	public void graphCleared(String sourceId, long timeId) {
		throw new RuntimeException(FileSinkTikZ.class.getName()
				+ " does not handle dynamic.");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.graphstream.stream.ElementSink#stepBegins(java.lang.String,
	 * long, double)
	 */
	public void stepBegins(String sourceId, long timeId, double step) {
		throw new RuntimeException(FileSinkTikZ.class.getName()
				+ " does not handle dynamic.");
	}
}
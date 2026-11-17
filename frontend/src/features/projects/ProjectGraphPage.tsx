import { useEffect, useMemo, useState } from 'react'
import { Tree, type NodeRendererProps } from 'react-arborist'

type GraphNode = { treeNodeId: string; artifactVersionId: string; groupId: string; artifactId: string; version: string; locallyManaged?: boolean; analysisStatus: string; hasChildren?: boolean }
type GraphEdge = { sourceTreeNodeId: string; targetTreeNodeId: string; relation: string; scope?: string }
type Graph = { projectId: string; analyzedAt?: string; analysisStatus: string; nodes: GraphNode[]; edges: GraphEdge[] }

const nodeWidth = 190
const nodeHeight = 58
const columnGap = 100
const rowGap = 30

function childrenByNode(graph: Graph) {
  const children = new Map<string, GraphEdge[]>()
  graph.edges.filter((edge) => edge.relation === 'DEPENDENCY').forEach((edge) => {
    children.set(edge.sourceTreeNodeId, [...(children.get(edge.sourceTreeNodeId) ?? []), edge])
  })
  return children
}

type TreeItem = { id: string; sourceId: string; label: string; status: string; placeholder?: boolean; children?: TreeItem[] }

function treeData(graph: Graph): TreeItem[] {
  const root = graph.nodes[0]
  if (!root) return []
  const nodes = new Map(graph.nodes.map((node) => [node.treeNodeId, node]))
  const children = childrenByNode(graph)
  const build = (nodeId: string, path: Set<string>, occurrenceId: string): TreeItem => {
    const node = nodes.get(nodeId)!
    const nextPath = new Set(path).add(nodeId)
    const childItems = (children.get(nodeId) ?? []).flatMap((edge) => nextPath.has(edge.targetTreeNodeId) ? [] : [build(edge.targetTreeNodeId, nextPath, `${occurrenceId}/${edge.targetTreeNodeId}`)])
    if (!childItems.length && node.hasChildren) childItems.push({ id: `${occurrenceId}/__lazy`, sourceId: nodeId, label: 'Rozwiń, aby załadować zależności…', status: '', placeholder: true })
    return { id: occurrenceId, sourceId: nodeId, label: `${node.groupId}:${node.artifactId}:${node.version}`, status: node.analysisStatus, children: childItems.length ? childItems : undefined }
  }
  return [build(root.treeNodeId, new Set(), root.treeNodeId)]
}

function DependencyTreeNode({ node, style }: NodeRendererProps<TreeItem>) {
  if (node.data.placeholder) return <div className="dependency-tree-node dependency-tree-placeholder" style={style} aria-hidden="true" />
  return <div className="dependency-tree-node" style={style} onClick={() => node.toggle()}>
    <button className="dependency-tree-toggle" type="button" aria-label={node.isOpen ? 'Zwiń zależności' : 'Rozwiń zależności'} disabled={node.isLeaf} onClick={(event) => { event.stopPropagation(); node.toggle() }}>{node.isLeaf ? '' : node.isOpen ? '−' : '+'}</button>
    <code>{node.data.label}</code><em>{node.data.status}</em>
  </div>
}

function GraphView({ graph, onExpand }: { graph: Graph; onExpand: (nodeId: string) => void }) {
  const { positions, width, height } = useMemo(() => {
    const root = graph.nodes[0]
    if (!root) return { positions: new Map<string, { x: number; y: number }>(), width: 900, height: 300 }
    const bySource = childrenByNode(graph)
    const depths = new Map<string, number>([[root.treeNodeId, 0]])
    const queue = [root.treeNodeId]
    while (queue.length) {
      const source = queue.shift()!
      ;(bySource.get(source) ?? []).forEach((edge) => { if (!depths.has(edge.targetTreeNodeId)) { depths.set(edge.targetTreeNodeId, (depths.get(source) ?? 0) + 1); queue.push(edge.targetTreeNodeId) } })
    }
    graph.nodes.forEach((node) => { if (!depths.has(node.treeNodeId)) depths.set(node.treeNodeId, 0) })
    const rows = new Map<number, number>()
    const result = new Map<string, { x: number; y: number }>()
    graph.nodes.forEach((node) => { const depth = depths.get(node.treeNodeId) ?? 0; const row = rows.get(depth) ?? 0; rows.set(depth, row + 1); result.set(node.treeNodeId, { x: 24 + depth * (nodeWidth + columnGap), y: 24 + row * (nodeHeight + rowGap) }) })
    const maxDepth = Math.max(...Array.from(depths.values()), 0)
    const maxRow = Math.max(...Array.from(rows.values()), 1)
    return { positions: result, width: 48 + (maxDepth + 1) * (nodeWidth + columnGap), height: 48 + maxRow * (nodeHeight + rowGap) }
  }, [graph])
  return <div className="graph-canvas"><svg className="dependency-graph" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Dependency graph">
    {graph.edges.filter((edge) => edge.relation === 'DEPENDENCY').map((edge) => { const source = positions.get(edge.sourceTreeNodeId); const target = positions.get(edge.targetTreeNodeId); if (!source || !target) return null; return <line key={`${edge.sourceTreeNodeId}-${edge.targetTreeNodeId}`} x1={source.x + nodeWidth} y1={source.y + nodeHeight / 2} x2={target.x} y2={target.y + nodeHeight / 2} className="dependency-edge" /> })}
    {graph.nodes.map((node) => { const position = positions.get(node.treeNodeId); if (!position) return null; const loaded = graph.edges.some((edge) => edge.sourceTreeNodeId === node.treeNodeId && edge.relation === 'DEPENDENCY'); return <g key={node.treeNodeId} transform={`translate(${position.x},${position.y})`} className={node.hasChildren && !loaded ? 'dependency-node-group expandable' : 'dependency-node-group'} onClick={() => node.hasChildren && !loaded && onExpand(node.treeNodeId)}><rect width={nodeWidth} height={nodeHeight} rx="10" className={node.locallyManaged ? 'dependency-node local' : 'dependency-node'} /><text x="12" y="23" className="dependency-node-title">{node.artifactId}</text><text x="12" y="43" className="dependency-node-version">{node.version}</text>{node.hasChildren && !loaded && <text x={nodeWidth - 22} y="23" className="dependency-node-expand">+</text>}</g> })}
  </svg></div>
}

function findTreeItem(items: TreeItem[], id: string): TreeItem | undefined { for (const item of items) { if (item.id === id) return item; const found = item.children && findTreeItem(item.children, id); if (found) return found } return undefined }

function TableView({ graph, onToggle }: { graph: Graph; onToggle: (item: TreeItem) => void }) { const data = useMemo(() => treeData(graph), [graph]); return <div className="dependency-tree-table">{data.length ? <Tree data={data} width="100%" height={560} rowHeight={32} indent={24} overscanCount={6} openByDefault={false} initialOpenState={{ [data[0].id]: true }} disableDrag disableDrop onToggle={(id) => { const item = findTreeItem(data, id); if (item) onToggle(item) }} aria-label="Dependency tree">{DependencyTreeNode}</Tree> : <span className="muted">Brak danych grafu. Uruchom analizę Maven.</span>}</div> }

export function ProjectGraphPage({ projectId }: { projectId: string }) {
  const [graph, setGraph] = useState<Graph>()
  const [error, setError] = useState(false)
  const [running, setRunning] = useState(false)
  const [view, setView] = useState<'graph' | 'table'>('graph')
  const [loadingNodes, setLoadingNodes] = useState<Set<string>>(new Set())
  async function load() { try { const response = await fetch(`/api/projects/${projectId}/dependency-graph`); if (!response.ok) throw new Error(); setGraph(await response.json()) } catch { setError(true) } }
  async function analyse() { setRunning(true); try { const response = await fetch(`/api/projects/${projectId}/dependency-analysis`, { method: 'POST' }); if (!response.ok) throw new Error(); const result = await response.json() as { jobId: string }; window.location.hash = `#jobs/${result.jobId}?return=graph/${projectId}` } catch { setError(true) } finally { setRunning(false) } }
  async function loadChildren(item: TreeItem) {
    if (item.placeholder || loadingNodes.has(item.sourceId)) return
    setLoadingNodes((current) => new Set(current).add(item.sourceId))
    try {
      const response = await fetch(`/api/projects/${projectId}/dependency-graph/${item.sourceId}/children`)
      if (!response.ok) throw new Error()
      const children = await response.json() as Graph
      setGraph((current) => current ? {
        ...current,
        nodes: [...current.nodes, ...children.nodes.filter((node) => !current.nodes.some((existing) => existing.treeNodeId === node.treeNodeId))],
        edges: [...current.edges, ...children.edges.filter((edge) => !current.edges.some((existing) => existing.sourceTreeNodeId === edge.sourceTreeNodeId && existing.targetTreeNodeId === edge.targetTreeNodeId && existing.relation === edge.relation))]
      } : current)
    } catch { setError(true) } finally {
      setLoadingNodes((current) => { const next = new Set(current); next.delete(item.sourceId); return next })
    }
  }
  useEffect(() => { load() }, [projectId])
  return <div className="page"><a className="back-link" href="#repositories">← Repositories</a><p className="eyebrow">UI-11</p><div className="page-heading"><div><h1>Dependency graph</h1><p className="mono muted">Project {projectId}</p></div><div className="workspace-actions"><button className="primary" onClick={analyse} disabled={running}>{running ? 'Uruchamianie…' : 'Uruchom analizę Maven'}</button></div></div>{error && <div className="state-card error-state">Nie udało się pobrać lub uruchomić analizy grafu.</div>}{!graph && !error && <div className="state-card">Ładowanie grafu…</div>}{graph && <><div className="graph-toolbar"><span>Status: <b>{graph.analysisStatus}</b></span><span>{graph.nodes.length} nodes · {graph.edges.length} edges</span><div><button className={view === 'graph' ? 'tab active' : 'tab'} onClick={() => setView('graph')}>Graph</button><button className={view === 'table' ? 'tab active' : 'tab'} onClick={() => setView('table')}>Tabela</button></div></div>{view === 'table' ? <TableView graph={graph} onToggle={loadChildren} /> : graph.nodes.length ? <GraphView graph={graph} onExpand={loadChildrenById => loadChildren({ id: loadChildrenById, sourceId: loadChildrenById, label: '', status: '' })} /> : <div className="graph-canvas"><span className="muted">Brak danych grafu. Uruchom analizę Maven.</span></div>}</>}</div>
}

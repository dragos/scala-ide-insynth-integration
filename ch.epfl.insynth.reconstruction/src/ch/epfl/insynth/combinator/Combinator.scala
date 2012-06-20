package ch.epfl.insynth.combinator

import scala.collection.mutable.PriorityQueue
import ch.epfl.insynth.{ env => InSynth }
import ch.epfl.insynth.trees._
import java.util.logging.Logger
import java.util.logging.ConsoleHandler
import ch.epfl.insynth.env.FormatNode
import java.util.logging.Level

/**
 * object which application transforms an InSynth representation input
 * to the pruned tree representation
 */
object Combinator extends ((InSynth.SimpleNode, Int) => Node) {
  
  val logger = Rules.logger
  val logApply = Rules.logApply//(logger.getName + ".apply")
  
  // predefined default number of combinations
  val predefinedNeededCombinations = 15
  
  // apply without specifying the number of combinations
  def apply(root: InSynth.SimpleNode):SimpleNode = {
    apply(root, predefinedNeededCombinations)
  }
  
  // apply with number of combinatinos needed
  def apply(root: InSynth.SimpleNode, neededCombinations: Int):SimpleNode = {
    // logging
    logApply.entering(getClass.getName, "apply")
    logApply.info("Entering combinator step (root: "+ FormatNode(root) + ", combinations: " + neededCombinations)
        
    // import transformer from InSynth to intermediate declaration
    import DeclarationTransformer.fromInSynthDeclaration
    
    // reset so that pruning is not done
    Rules.doPruning = false
      
    // pair type that will be put in the priority queue
    // ( expression to be explored, a set of expressions visited on the path )
    type ExpressionPair = (Expression, Set[InSynth.Node])
    // priority queue
    var pq = new PriorityQueue[ExpressionPair]() (
      // ordering defined on the expressions  
      new Ordering[ExpressionPair] {                                                                    
	    def compare(a : ExpressionPair, b : ExpressionPair) = b._1.compare(a._1)                                           
      } 
    )    
    // declare root Tree which starts the hierarchy 
    val rootTree:Tree = new TopTree(neededCombinations)
    // a single declaration of the root Tree is the one corresponding to the root node
    val rootDeclaration = Composite(rootTree, fromInSynthDeclaration(root.getDecls.head), root)
    
    /* start the traversal */
    
    // add the root declaration and an empty set
    val startTuple = (rootDeclaration, Set[InSynth.Node]())
    pq += startTuple
    
    // while there is something in the queue
    while (! pq.isEmpty) {
      
      // dequeue a pair
      val (currentDeclaration, visited) = pq.dequeue
              
      // logging
      if (Rules.isLogging) {
	      logApply.info("Current declaration processed " + currentDeclaration)
	            
	      // if current declaration is pruned or already visited on this path, log
	      if (currentDeclaration.isPruned) {
	        logApply.fine("Declaration with " + FormatNode(currentDeclaration.getAssociatedNode, true) + " pruned")
	      }
	      if (visited.contains(currentDeclaration.getAssociatedNode)) {
	        logApply.info("Stumbled upon a cycle (discarding the node: " + FormatNode(currentDeclaration.getAssociatedNode, true) + ")")
	      }
	      
	      // additional pruning conditions
	      if (currentDeclaration.getAssociatedTree.isPruned) {
	        logApply.fine("!Declaration with " + FormatNode(currentDeclaration.getAssociatedNode, true) + " pruned")
	      }
	      if (Rules.doPruning && currentDeclaration.getAssociatedTree.checkIfPruned(currentDeclaration.getTraversalWeight)) {
	        logApply.fine("!!Declaration with " + FormatNode(currentDeclaration.getAssociatedNode, true) + " pruned")
	      }
      }
        
      // if current declaration is pruned or already visited on this path, ignore it
      if (
          !visited.contains(currentDeclaration.getAssociatedNode) && !currentDeclaration.isPruned &&
          !currentDeclaration.getAssociatedTree.isPruned
          ) {
      
    	  // add explored declaration to its associated tree as explored
	      currentDeclaration.getAssociatedTree addDeclaration(currentDeclaration)
	      
          // logging
	      if (Rules.isLogging)
          logApply.info("Adding expression " + currentDeclaration.toString + " to its tree")
	      
	      // check the type of the current declaration
	      currentDeclaration match {
	        // Composite represents a declaration with children 
	        case c:Composite => {
	          // get all needed parameters
	          val paramList = c.origDecl.getType match {
	            case Arrow(TSet(list), _) => list
	            case _ => throw new RuntimeException
	          }          
	          // for each its parameter add child to the queue for later traversing
	          for (parameter <- paramList) {
	            // create a tree for a parameter and associate it with the current composite
	            val paramTree = new Tree(c, parameter)
	            c.addChild(paramTree)
	            
	            // for each child node in associated InSynth nodes for the parameter
	            for(node <- c.associatedNode.getParams(parameter).nodes) {
	              // according to the type of node
	              // create a declaration and insert the pair into the queue
	              
	              // if there are no parameters insert simple nodes
	              if (node.getParams.isEmpty){
	                  // for each of its declarations
	                  for (dec <- node.getDecls;
	                  // optimization - only consider those nodes that have less weight than
	                  // all trees up to the tree
	                  if !Rules.doPruning && !paramTree.checkIfPruned(dec.getWeight.getValue
                		  + paramTree.getTraversalWeight)) {
	                    	                    
	                    // new pair of simple expression and extended path
	                    val newPair = 
	                      (
                            Simple(paramTree, fromInSynthDeclaration(dec), node),
                            visited + c.getAssociatedNode
                		  )
	                      
                        // logging
                		if (Rules.isLogging)
	                    logApply.fine("Adding simple " + dec.getSimpleName + " to the queue")
                        // add new pair to the queue 
	                    
                    	pq += newPair
	                  }	                
                  // if there are parameters insert composite nodes
	              } else {
	                  // for each of its declarations
	                  for (dec <- node.getDecls 
	                  // optimization - only consider those nodes that have less weight than
	                  // all trees up to the tree
	                  if !Rules.doPruning && !paramTree.checkIfPruned(dec.getWeight.getValue
                		  + paramTree.getTraversalWeight)) {
	                    // new pair of simple expression and extended path
	                    val newPair = 
	                      (
                            Composite(paramTree, fromInSynthDeclaration(dec), node), 
	                        visited + c.getAssociatedNode
                		  )
	                      	                      
                        // logging
                		if (Rules.isLogging)
	                    logApply.fine("Adding composite " + dec.getSimpleName + " to the queue")
	                    
                        // add new pair to the queue 
	                    pq += newPair
	                  }	                
	              }
	            }
	          }
	        }
	        // Simple has no children 
	        case s:Simple =>
	          // mark it as done
	          s.associatedTree.childDone(s)
            // should not happen we do not deal with these anymore
	        case l:LeafExpression => throw new RuntimeException
//	        case l:LeafExpression => l.associatedTree.childDone(l)
	      }
      }
    }
    
    // return transformed pruned tree as a result
    val result = rootDeclaration.toTreeNode
    
    // logging
    if (Rules.isLogging) {
    if (rootDeclaration.isPruned) {
      logger.log(Level.SEVERE, "Root declaration is pruned!")
    }
    if (!rootDeclaration.isDone) {
      logger.log(Level.SEVERE, "Root declaration is not done!")
    }
    logApply.exiting(getClass.getName, "apply")
          
    // log
    logger.finest("Returning from apply with result (reconstruction structures): " + FormatCombinations(rootDeclaration) )
    logger.fine("Returning from apply with result: " + FormatPrNode(result) )
    logger.info("Number of combinations found: " + rootDeclaration.getNumberOfCombinations )
    }
    
    result
  }
  
}
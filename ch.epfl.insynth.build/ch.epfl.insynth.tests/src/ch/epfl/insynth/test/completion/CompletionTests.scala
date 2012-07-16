package ch.epfl.insynth.test.completion

import scala.tools.eclipse.testsetup.SDTTestUtils
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.nsc.interactive.Response
import scala.tools.eclipse.ScalaWordFinder
import scala.tools.nsc.util.SourceFile
import scala.tools.eclipse.ScalaPresentationCompiler
import org.eclipse.jface.text.contentassist.ICompletionProposal
import org.junit.Assert._
import org.junit.Test
import scala.tools.eclipse.testsetup.TestProjectSetup
import org.eclipse.jdt.core.search.{ SearchEngine, IJavaSearchConstants, IJavaSearchScope, SearchPattern, TypeNameRequestor }
import org.eclipse.jdt.core.IJavaElement
import org.junit.Ignore
import scala.tools.nsc.util.OffsetPosition
import scala.tools.eclipse.completion.ScalaCompletions
import scala.tools.eclipse.completion.CompletionProposal
import ch.epfl.insynth.core.completion.InsynthCompletionProposalComputer
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.core.runtime.NullProgressMonitor
import ch.epfl.insynth.core.completion.InnerFinder
import scala.collection.JavaConversions
import scala.collection.JavaConverters

object CompletionTests extends TestProjectSetup("completion")

class CompletionTests {
  import CompletionTests._

  import org.eclipse.core.runtime.IProgressMonitor
  import org.eclipse.jface.text.IDocument
  import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext

  private def withCompletions(path2source: String): List[List[ICompletionProposal]] = {
    val unit = compilationUnit(path2source).asInstanceOf[ScalaCompilationUnit]
    
    // first, 'open' the file by telling the compiler to load it
    project.withSourceFile(unit) { (src, compiler) =>
      val dummy = new Response[Unit]
      compiler.askReload(List(src), dummy)
      dummy.get

      val tree = new Response[compiler.Tree]
      compiler.askType(src, true, tree)
      tree.get

      val contents = unit.getContents
      // mind that the space in the marker is very important (the presentation compiler 
      // seems to get lost when the position where completion is asked 
      val positions = SDTTestUtils.positionsOf(contents, " /*!*/")
      val content = unit.getContents.mkString

      assertTrue("cotent= " + content +  " positions.size=" + positions.size, positions.size > 0)
      
      val completion = new ScalaCompletions
      
      (List[List[ICompletionProposal]]() /: (0 until positions.size) ) {
        (list, i) => {
          val pos = positions(i)

		  val position = new scala.tools.nsc.util.OffsetPosition(src, pos)
          var wordRegion = ScalaWordFinder.findWord(content, position.point)

          import scala.collection.JavaConverters._
        
          list :+ InnerFinder(unit, pos).asScala.toList
        }
      }
      
//      for (i <- 0 until positions.size) {
//        val pos = positions(i)
//
//        val position = new scala.tools.nsc.util.OffsetPosition(src, pos)
//        var wordRegion = ScalaWordFinder.findWord(content, position.point)
//
//        import scala.collection.JavaConverters._
//        
//        body(i, position, InnerFinder(unit, pos).asScala.toList : List[ICompletionProposal])
//      }
    } (  )
  }
  
  type Checker = List[ICompletionProposal]=>Unit
  
  private def checkCompletions(path2source: String)(expectedProperties: List[Checker]*) {

    for ( 
        (calculatedList, expectedList) <- (withCompletions(path2source) zip expectedProperties);
		expected <- expectedList
        ) {
      expected(calculatedList)
    }
  }
  
  case class CheckContains(expectedCompletions: List[String]) extends Checker {

    def apply(completions: List[ICompletionProposal]) = {
      val calculatedStrings = completions.map { _.getDisplayString.trim }
      for (expected <- expectedCompletions) {
        val contains = calculatedStrings contains expected
        assertTrue("Expected snippet: " + expected + ", calculated snippets: " + calculatedStrings.mkString(", "), contains)
      }
    }
  }  
  
  case class CheckNumberOfCompletions(expectedNumber: Int) extends Checker {

    def apply(completions: List[ICompletionProposal]) = {
      assertEquals(expectedNumber, completions.size)
    }
  }  
  
  case class CheckRegexContains(expectedCompletions: List[String]) extends Checker {
    def apply(completions: List[ICompletionProposal]) = {
      val calculatedStrings = completions.map { _.getDisplayString.trim }
      for (expected <- expectedCompletions) {
        val contains = (false /: calculatedStrings) {
          (result, string) => result || (string matches expected)
        }
        assertTrue("Expected snippet: " + expected + ", calculated snippets: " + calculatedStrings.mkString(", "), contains)
      }
    }
  }

  /**
   * Transform the given completion proposal into a string that is (hopefully)
   *  compiler-version independent.
   *
   *  Transformations are:
   *    - remove parenthesis
   *    - java.lang.String => String
   */
  private def normalizeCompletion(str: String): String = {
    str.replace("(", "").replace(")", "").replace("java.lang.String", "String")
  }

  /**
   * Test that completion shows only accessible members.
   */
  @Test
  def testExample1() {
    val oraclePos11 = List("A m", "0")
    
    val exampleCompletions = List(CheckContains(oraclePos11), CheckNumberOfCompletions(5))
    
    checkCompletions("examplepkg1/Example1.scala")(exampleCompletions)
  }
  
  @Test
  def testExample2() {
    val oraclePos14 = List("new A().a()", "new A() m b")
    
    val exampleCompletions = List(CheckContains(oraclePos14))
    
    checkCompletions("examplepkg2/Example2.scala")(exampleCompletions)
  }
  
  @Test
  def testExample3() {
    val oraclePos12regex = List("new A\\(\\) m1 \\{ (\\S+) => new A\\(\\) m2 \\1 \\}",
        "new A\\(\\) m1 \\{ (\\S+) => new A\\(\\) m2 l1 \\}")
    val oraclePos12strings = List("\"?\"")
    
    val exampleCompletions = List(CheckRegexContains(oraclePos12regex), CheckContains(oraclePos12strings))
    
    checkCompletions("examplepkg3/Example3.scala")(exampleCompletions)
  }

  @Test
  @Ignore("Enable this test once the ticket is fixed.")
  def t1001014 {
    val oracle = List("xx")
    
    val unit = scalaCompilationUnit("t1001014/F.scala")
    reload(unit)

    //runTest("t1001014/F.scala", false)(oracle)
  }
}
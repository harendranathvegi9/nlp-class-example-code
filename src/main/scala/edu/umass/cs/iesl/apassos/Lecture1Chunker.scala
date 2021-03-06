package edu.umass.cs.iesl.apassos

import cc.factorie.app.nlp._
import cc.factorie.app.nlp.pos.PTBPosLabel
import cc.factorie.{FeatureVectorVariable, CategoricalTensorDomain, LabeledCategoricalVariable, CategoricalDomain}
import cc.factorie.optimize.{LinearMultiClassClassifier, OnlineLinearMultiClassTrainer}
import scala.annotation.tailrec

/**
 * User: apassos
 * Date: 9/10/13
 * Time: 5:43 PM
 */

object ChunkLabelDomain extends CategoricalDomain[String] {
  this ++= Seq("IN", "OUT")
  freeze()
}
class ChunkLabel(target: String) extends LabeledCategoricalVariable[String](target) {
  def domain = ChunkLabelDomain
}

class Lecture1Chunker extends DocumentAnnotator {
  def prereqAttrs = Seq(classOf[PTBPosLabel])
  def postAttrs = Seq(classOf[ChunkLabel])
  def tokenAnnotationString(token: Token) = token.attr[ChunkLabel].categoryValue

  val featuresDomain = new CategoricalTensorDomain[String] {}
  class TokenFeatures extends FeatureVectorVariable[String] {
    def domain = featuresDomain
    override def skipNonCategories = true
  }
  var model: LinearMultiClassClassifier = null

  def extractFeatures(t: Token): TokenFeatures = {
    val f = new TokenFeatures
    for (tt <- t.prevWindow(3) ++ t.nextWindow(3); dif = t.positionInSection - tt.positionInSection) {
      f += s"STRING@$dif=${tt.string}"
      f += s"STRING@$dif=${tt.posLabel}"
    }
    f
  }

  def process(document: Document) = {
    document.tokens.foreach(t => {
      val feats = extractFeatures(t)
      t.attr += new ChunkLabel(ChunkLabelDomain.categories(model.classification(feats.value).bestLabelIndex))
    })
    document
  }

  @tailrec
  private final def isInChunk(s: Sentence, t: Token): Boolean = {
    if (t.posLabel.categoryValue.startsWith("N")) true
    else if (s.parse.parent(t) == null) false
    else isInChunk(s, s.parse.parent(t))
  }

  def train(trainSentences: Iterable[Sentence], testSentences: Iterable[Sentence])(implicit rng: scala.util.Random) {
    val trainLabels = trainSentences.flatMap(s => s.tokens.map(t => new ChunkLabel(if (isInChunk(s, t)) "IN" else "OUT"))).toSeq
    val testLabels = testSentences.flatMap(s => s.tokens.map(t => new ChunkLabel(if (isInChunk(s, t)) "IN" else "OUT"))).toSeq
    val trainFeatures = trainSentences.flatMap(s => s.tokens.map(extractFeatures)).toSeq
    featuresDomain.freeze()
    val testFeatures = testSentences.flatMap(s => s.tokens.map(extractFeatures)).toSeq
    val trainer = new OnlineLinearMultiClassTrainer()
    model = trainer.train(trainLabels, trainFeatures, testLabels, testFeatures)
  }

}

object Lecture1Chunker {

  def main(args: Array[String]) = {
    implicit val rng = new scala.util.Random(0)
    val trainDoc = LoadOntonotes5.fromFilename("/iesl/canvas/mccallum/data/ontonotes-en-1.1.0/trn-pmd/nw-wsj-trn.dep.pmd").head
    val testDoc = LoadOntonotes5.fromFilename("/iesl/canvas/mccallum/data/ontonotes-en-1.1.0/dev-pmd/nw-wsj-24.dep.pmd").head
    val model = new Lecture1Chunker
    model.train(trainDoc.sentences, testDoc.sentences)
    model.process(testDoc)
    println(testDoc.owplString(Seq(model.tokenAnnotationString)))
  }
}

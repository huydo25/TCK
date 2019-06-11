package com.tck.main

import scala.io.Source
import com.tck.models.trainTCK._
import com.tck.models.TCK._

object Main{

  def readCSV(filename:String) : Array[Array[Double]] = {
    Source.fromFile(filename)
      .getLines()
      .map(_.split(",").map(_.trim.toDouble))
      .toArray
  }
  def main(args: Array[String]): Unit = {
    val resourcesPath = getClass.getResource("/x_VAR.csv")
    val x = readCSV(resourcesPath.getPath)
    val resourcesPath1 = getClass.getResource("/xte_VAR.csv")
    val xte = readCSV(resourcesPath1.getPath)
    //val x_new = DenseMatrix(x:_*)
    // println(x.length, x(0).length)

    // Reshape raw data into MTS
    var X : Array[Array[Array[Double]]] = Array.ofDim[Double](200,50,2)
    for(i <- 0 until x.length ){
      for (j <- 0 until x(i).length){
        if ( j < (x(i).length/2)){
          X(i)(j)(0) = x(i)(j)
          //println(j, x(i)(j))
        }
        else{
          X(i)(j-x(i).length/2)(1) = x(i)(j)
          //println(j-(x(i).length/2),x(i)(j))
        }
      }
    }
    // label data
    var Y = Array.fill(200)(1)
    for (i <- 100 until  Y.length  ){
      Y(i) += 1
    }

    // Reshape xte data into MTS
    var Xte : Array[Array[Array[Double]]] = Array.ofDim[Double](200,50,2)
    for(i <- 0 until xte.length -1 ){
      for (j <- 0 until xte(i).length){
        if ( j < (xte(i).length/2)){
          Xte(i)(j)(0) = xte(i)(j)
        }
        else{
          Xte(i)(j-x(i).length/2)(1) = xte(i)(j)
        }
      }
    }
    val Yte =Y
    //print(Yte.deep.mkString(" "))
    var gmmParameter : List[(Array[Array[Double]], Array[Array[Array[Double]]], Array[Array[Double]],Array[Double], Array[Int], Array[Int])]  = List()
    var C: Int = 0
    var G: Int = 0

    var temp_r = trainTCK(X)
    gmmParameter = temp_r._1
    C = temp_r._2
    G = temp_r._3
//    println(C,G)
//    println(gmmParameter(0)._1.deep.mkString("\n"))
//    (gmmParameter, C, G) = trainTCK(X)

    // Compute in-sample kernel matrix
    var K = TCK(gmmParameter,C,G,1)

    // % Compute similarity between Xte and the training points
    var Kte = TCK(gmmParameter,C,G,0,Xte);
//    println(Kte.length, Kte(0).length)
//    println(Kte.deep.mkString("\n"))
    // 1NN -classifier
    val Nte = Yte.length
    //println(Nte)
    var I : Array[Int] = Array.ofDim(Kte(0).length)
    for (i <- 0 until Kte(0).length){
      val temp = Kte.map(_(i))
//      println(temp.max)
      I(i) = temp.indexOf(temp.max)
    }
    //println(I.deep.mkString(" "))
    var predY : Array[Int] = Array.fill(I.length)(1)
    for (i <- I){
      predY(i) = Y(i)
      //println(i, Y(i))
      //println(i, predY(i))
      //println()
    }
    //println(predY.deep.mkString(" "))
    var sum : Int = 0
    for (i <- 0 until Yte.length ){
      if (predY(i) == Yte(i)){
        sum  = sum + 1
      }
    }
    val accuracy : Double = sum.toDouble / Nte.toDouble * 100
    println()
    println(accuracy)
    println("Done!!")
  }


}
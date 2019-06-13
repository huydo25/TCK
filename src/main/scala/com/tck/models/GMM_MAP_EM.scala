package com.tck.models

import breeze.linalg.{Axis, DenseMatrix, DenseVector, det, diag, inv, sum}

import scala.math._
import breeze.numerics.{pow}
import com.tck.models.GMMposterior._
import com.tck.utils.Utils._

import scala.util.Random

object  GMM_MAP_EM{
  def GMM_MAP_EM(x: Array[Array[Array[Double]]],
                  C: Int = 40, minN: Double = 0.8,
                  minV: Int = 2, maxV: Int = 100,
                  minT : Int = 6, maxT: Int = 25,
                  I: Int = 20,  missing: Int = 0) :
                  (Array[Array[Double]], Array[Array[Array[Double]]], Array[Array[Double]],Array[Double], Array[Int], Array[Int]) = {
//    MAP_EM - fit a GMM to time series data with missing values using MAP-EM
//
//    INPUTS
//      X: data array of size N x T x V
//      C: number of mixture components (optional)
//      minN: min percentage of subsample (optional)
//      minV: min number of dimensions (optional)
//      maxV: max number of dimensions (optional)
//      minT: min length of time segments (optional)
//      maxT: max length of time segments (optional)
//      I: number of iterations (optional)
//      missing: binary indicator. 1 if there is missing data and 0 if not
//
//    OUTPUTS
//      Q: cluster posterior probabilities
//      mu: cluster means (time dependant + variable dependant)
//      s2: cluster variances (variable dependant)
//      theta: cluster priors
//      dim_idx: indexes of the subset of dimension considered
//      time_idx: indexes of the subset of time intervals considered
//
//      Reference: "Time Series Cluster Kernel for Learning Similarities between Multivariate Time Series with Missing Data", 2017 Pattern Recognition, Elsevier.

    val N = x.length
    val T = x(0).length
    val V = x(0)(0).length
    // optional parameters
    assert(minN > 0 && minN <= 1, "The minimum percentage of subsample must be in (0,1]" )
//    if (V == 1){
//      val minV = 1
//    } else{
//      val minV = 2
//    }
    assert(minV >= 1 && minV <= V, "The maximum number of variables must be in [1,V]")
    assert(maxV >= 1 && maxV <= V, "The maximum number of variables must be in [1,V]")
    assert(minT >= 1 && minT <= T, "The minimum number of variables must be in [1,T]")
    assert(maxT >= 1 && maxT <= T, "The maximum number of variables must be in [1,T]")
    var maxT_new = 0.0
    maxT_new = math.min(math.floor(0.8*T), maxT)

    // Hyperparameters for mean prior (a0, b0) and the std dev prior (n0) of the mixture components
//    val a0 : Double = ( 1.0 - 0.001 ) * random + 0.001
//    val b0 : Double = ( 0.2 - 0.005 ) * random + 0.005
//    val n0 : Double = ( 0.2 - 0.001 ) * random + 0.001
    val a0 = 0.8409
    val b0 = 0.0546
    val n0 = 0.1630
    //Randomly subsample dimensions, time intervals and samples
    var sN: Int = 0
    if(N > 100){
//      sN = math.round(minN*N).toInt + Random.nextInt(N - math.round(minN*N).toInt +1)
      sN = 180
    } else {
      sN = math.round(0.9*N).toInt
    }
    var sub_idx: Array[Int] = Array.ofDim(sN)
//    sub_idx = Random.shuffle(0 to N-1).take(sN).sortWith(_<_).toArray
    sub_idx = (0 until sN).toArray

    val sV =  minV + Random.nextInt(maxV - minV +1)
    val dim_idx : Array[Int] = Random.shuffle(0 to V-1).take(sV).sortWith(_<_).toArray//generate sV (sorted) integers between 1 and V

    val t1 =  1 + Random.nextInt(T-minT+1)
    val t2 = (t1 + minT - 1) + Random.nextInt(min(T,(t1 + maxT -1)) - (t1 + minT -1) +1) - 1
//    val sT = t2 - t1 +1
    val sT = 20
//    val time_idx: Array[Int] = (t1 to t2).toArray // generate sT continuous integers from t1 to t2
    val time_idx = (10 to 29).toArray

    val sX: Array[Array[Array[Double]]] = Array.ofDim(sub_idx.length, time_idx.length, dim_idx.length)
    for (i <- 0 until sN){
      for (j <- 0 until sT){
        for (k <- 0 until sV ){
          sX(i)(j)(k) = x(sub_idx(i))(time_idx(j))(dim_idx(k))
        }
      }
    }
    //println(sX.deep.mkString("\n"))
    // initialize model parameters
    var theta : Array[Double] = Array.fill(C)(1/C)                  // cluster priors        (1 x C)
    var mu : Array[Array[Array[Double]]]= Array.fill(sT, sV, C)(0.0) // cluster means         (sT x sV x C)
    var s2 : Array[Array[Double]] = Array.fill(sV, C)(0.0)            // cluster variances     (sV x C)
    var Q : Array[Array[Double]] = Array.fill(sN, C)(0.0)             // cluster assignments   (sN x C)

    if (missing == 1 ){
      // handle missing data
      val nan_idx = isNaN(sX)
      var R: Array[Array[Array[Double]]] = Array.fill(sN,sT,sV)(1)
      for (i <- 0 until sX.length){
        for (j <- 0 until sX(i).length){
          for (k <- 0 until sX(i)(j).length){
            if (nan_idx(i)(j)(k) == 1 ){
              R(i)(j)(k) = 0
             //sX(i)(j)(k) = -100000
            }
          }
        }
      }

    //Calculate empirical moments
      // init variable
      var mu_0 : Array[Array[Double]] = Array.fill(sT,sV)(0) // prior mean over time and variables (sT x sV)
      var temp: Array[Array[Array[Double]]]= Array.ofDim(sX(0)(0).length, sX(0).length, sX.length)
      var temp1 : Array[Array[Double]] = Array.ofDim( sX(0).length,  sX.length)
      // reshape array
      for (j <- 0 until sX(0)(0).length) {
        // reset value of array then assign
        temp1 = Array.fill( sX(0).length,  sX.length)(0)
        for (i <- 0 until sX(0).length) {
          temp1(i) = sX.map(_(i)).map(_(j))
        }
        temp(j) = temp1
      }
      // mean over the array
      for (i <- 0 until temp(0).length){
        //      println(temp.length, temp(0).length, temp(0)(0).length)
        var a = nanmean2D(temp.map(_(i)), 1)
        mu_0(i) = a
      }
      // reshape 3D array to 2D
      var s_0: Array[Double] = Array.fill(sV)(1) // prior std over variables (sV x 1)
      var tempX : Array[Array[Double]] = Array.ofDim(sV, sN*sT)
      // println(tempX.length, tempX(0).length)
      for (i <- 0 until sX(0)(0).length){
        var temp:  Array[Double] = Array()
        for (j <- 0 until sX(0).length ){
          temp = temp ++ sX.map(_(j)).map(_(i))
        }
        //println(temp.deep.mkString("\n"))
        tempX(i) = temp
      }
     // println(tempX.deep.mkString("\n"))
      //println("\n")
      tempX = tempX.transpose
      s_0 = nanstd2D(tempX,0,0)
      var s2_0 = s_0.map(x => x*x)

      val S_0, invS_0 : Array[Array[Array[Double]]] = Array.fill(sV, sT, sT)(0)

      val T1 : Array[Array[Int]] = Array.fill(sT)(1 to sT toArray).transpose
      val T2 : Array[Array[Int]] = Array.fill(sT)(1 to sT toArray)
      var  r: Array[Array[Double]] = Array()

      for (v <- 0 until sV){
        r = Array.ofDim(T1.length,T1.length)
        for (i <- 0 until T1.length){
          for (j <- 0 until T1.length){
            r(i)(j) = s_0(v) * b0 * math.exp(-a0 * math.pow((T1(i)(j) - T2(i)(j)),2))
          }
        }
        //S_0(v) = r
        // if matrix is inverted
        var t = DenseMatrix(r:_*)
        if ( det(t) != 0 ){  // check if the matrix can be inverted
          t = t + 0.1*t(1,1)* DenseMatrix.eye[Double](T1.length)  // add a small number to the diagonal
        }
        val inv_t = inv(t)
        for (i <- 0 until inv_t.rows ){
          r(i) = inv_t(::,i).toArray
        }
        invS_0(v) = r
      }

      // initialize model parameters
        theta = Array.fill(C)(1/C)                  // cluster priors        (1 x C)
        mu = Array.fill(sT, sV, C)(0) // cluster means         (sT x sV x C)
        s2  = Array.fill(sV, C)(0)            // cluster variances     (sV x C)
        Q = Array.fill(sN, C)(0)             // cluster assignments   (sN x C)

      for (i <- 0 until x.length){
        for (j <- 0 until x(i).length){
          for (k <- 0 until x(i)(j).length){
            if (nan_idx(i)(j)(k) == 1 ){
              sX(i)(j)(k) = -100000
            }
          }
        }
      }

      for (i <- 0 until I){
        // initialization: random clusters assignment
        if (i == 1){
          val cluster: Array[Int] = Array.fill(sN)(Random.nextInt(C))
          val temp: Array[Int] = (1 to C).toArray
          Q.indices.map(i => Q(i).indices.map(j => if (cluster(i) == temp(j)) Q (i)(j) = 1 else Q (i)(j) = 0))
        }
        // update clusters assignment
        else{
          // start
          var distr_c : Array[Array[Array[Double]]] = Array.ofDim(sN, sT, sV)
          for (c <- 0 until C){
            var temp: Double = 0
            for (j <- 0 until sN){
              for (k <- 0 until sT) {
                for (l <- 0 until sV){
                  // need to update in term of dimension array
                  temp = normpdf(sX(j)(k)(l), mu(k)(l)(c), s2(l)(c))
                  if (temp < normpdf(3)) {
                    temp = normpdf(3)
                  }
                  distr_c(j)(k)(l) = temp
                }
              }
            }
            //reshape distribution vector
            val temp1: Array[Array[Double]] = Array.ofDim(sN, sV*sT)
            for (l <- 0 until sN){
              for (j <- 0 until sT){
                for (k <- 0 until sV){
                  temp1(l)(k*sT+j) = distr_c(l)(j)(k)
                }
              }
            }
            // product of distribution function
            val prod_distrc_c : Array[Double] = Array.fill(sN)(1)
            for (j <- 0 until sN){
              for (k <- 0 until sV*sT){
                prod_distrc_c(j) *= temp1(j)(k)
              }
            }
            for (j <- 0 until sN){
              Q(j)(c) = prod_distrc_c(j) * theta(c)
            }
            //end
          }
          val sum = Array.fill(C)(Q.map(_.sum)).transpose
          Q.indices.map(i =>  Q(i).indices.map(j => Q(i)(j)/= sum(i)(j)))
          //end
        }
        // update mu, s2 and theta
        for (c <- 0 until C){
          val sumQ = Q.map(_(c)).sum
          theta(c) = sumQ/sN
          for (v <- 0 until sV ){
            val var2 = DenseVector(R.map(_.map(_(v)).sum):_*).t * DenseVector(Q.map(_(c)):_*) // be careful with transpose
            val temp_var2 = DenseMatrix(sX.map(_.map(_(v))):_*) - DenseMatrix(Array.fill(sN)(mu.map(_.map(_(c))).map(_(v))):_*).map(x => x*x)
            val var1 = DenseVector(Q.map(_(c)):_*).t dot
                                          sum(DenseMatrix(R.map(_.map(_(v))):_*) :* temp_var2, Axis._1).t

            s2(v)(c) = (n0*s2_0(v) + var1) / (n0 + var2)
            val A = DenseMatrix(invS_0(v):_*) + diag(DenseMatrix(R.map(_.map(_(v))):_*).t * DenseMatrix(Q.map(_(c)):_*) / s2(v)(c)).toDenseMatrix
            val b = DenseMatrix(invS_0(v):_*) * DenseMatrix(mu_0.map(_(v)):_*) :* DenseMatrix(sX.map(_.map(_(v))):_*).t *  DenseMatrix(Q.map(_(c)):_*) / s2(v)(c)
            val temp_r : Array[Double] = (A \ b).toArray
            mu.map(_.map(_(c))).map(_(v)).indices.map(x => mu(x)(v)(c) = temp_r(x) )
          }
        }
      } // end for i=1:I

      // compute assignments for all data
      Q  = GMMposterior.GMM_posterior( x, C, mu, s2, theta, dim_idx, time_idx, missing)

    }  else if (missing == 0 ){
      //Calculate empirical moments
      // init variable
      var mu_0 : Array[Array[Double]] = Array.fill(sT,sV)(0) // prior mean over time and variables (sT x sV)

      var rsX: Array[Array[Array[Double]]]= Array.ofDim(sV, sT, sN)

      // reshape array
      for (j <- 0 until sV) {
        // reset value of array then assign
        var temp1 : Array[Array[Double]] = Array.fill(sT,sN)(0)
        for (i <- 0 until sT) {
          temp1(i) = sX.map(_(i)).map(_(j))
        }
        rsX(j) = temp1
      }
      // mean over the array
      for (i <- 0 until sT){
        var a = nanmean2D(rsX.map(_(i)), 1)
        mu_0(i) = a
      }
      // reshape 3D array to 2D
      var s_0: Array[Double] = Array.fill(sV)(1) // prior std over variables (sV x 1)
      var tempX : Array[Array[Double]] = Array.ofDim(sV, sN*sT)

      for (i <- 0 until sV){
        var t:  Array[Double] = Array()
        for (j <- 0 until sT ){
          t = t ++ sX.map(_(j)).map(_(i))
        }
        tempX(i) = t
      }
      tempX = tempX.transpose
      s_0 = nanstd2D(tempX,0,0)
      var s2_0 = s_0.map(x => x*x)

      val S_0, invS_0 : Array[Array[Array[Double]]] = Array.fill(sV, sT, sT)(0)

      val T1 : Array[Array[Int]] = Array.fill(sT)(1 to sT toArray).transpose
      val T2 : Array[Array[Int]] = Array.fill(sT)(1 to sT toArray)
      var  r: Array[Array[Double]] = Array()
      for (v <- 0 until sV){
        r = Array.ofDim(T1.length,T1.length)
        for (i <- 0 until T1.length){
          for (j <- 0 until T2.length){
            r(i)(j) = s_0(v) * b0 * exp(-a0 * math.pow((T1(i)(j) - T2(i)(j)),2))
          }
        }
        //S_0(v) = r
        // if matrix is inverted
        var t = DenseMatrix(r:_*)
        if ( det(t) == 0 ){  // check if the matrix can be inverted
          t = t + 0.1*t(1,1)* DenseMatrix.eye[Double](T1.length)  // add a small number to the diagonal if S_0 is not invertible
        }
        val inv_t = inv(t)
        //println(inv_t.toString(20,Int.MaxValue))
        for (i <- 0 until inv_t.rows ){
          r(i) = inv_t(::,i).toArray
        }
        invS_0(v) = r
      }
      // no errors  until there

      // initialize model parameters
       theta = Array.fill(C)(1.0/C)                  // cluster priors        (1 x C)
       mu = Array.fill(sT, sV, C)(0.0)             // cluster means         (sT x sV x C)
       s2 = Array.fill(sV, C)(0.0)                   // cluster variances     (sV x C)
       Q  = Array.fill(sN, C)(0.0)                   // cluster assignments   (sN x C)

      for (i <- 0 until I){
        // initialization: random clusters assignment
        if (i == 0){
          //val cluster: Array[Int] = Array.fill(sN)(Random.nextInt(C))
          val cluster : Array[Int] = Array(3,8,2,8,3,1,9,7,10,10,6,10,5,9,1,2,8,4,4,7,1,4,1,7,6,1,5,5,7,3,1,5,8,7,10,3,3,3,9,8,9,8,7,5,6,10,10,4,2,5,6,1,9,9,5,3,8,7,5,2,8,9,2,10,1,5,1,3,8,7,3,4,1,1,8,4,6,10,7,5,7,7,3,9,6,5,8,6,3,1,6,7,6,6,5,4,5,8,10,3,8,2,1,5,6,3,2,7,4,6,7,6,6,8,7,4,9,7,6,1,9,2,9,4,5,8,2,7,10,7,1,7,3,7,4,6,3,10,4,1,1,7,10,4,8,3,2,6,4,10,3,6,6,7,6,3,2,8,1,9,5,9,10,5,1,8,7,6,6,4,8,4,9,4,8,9,1,5,10,5)
          val temp: Array[Int] = (1 to C).toArray
          Q.indices.map(i => Q(i).indices.map(j => if (cluster(i) == temp(j)) Q (i)(j) = 1 else Q (i)(j) = 0))
          //println(Q.deep.mkString("\n"))
        }
        // update clusters assignment
        else{
          // start
          var distr_c : Array[Array[Array[Double]]] = Array.ofDim(sN, sT, sV)
          //println(sX.length,sX(0).length, sX(0)(0).length)
          //println(sN, sT, sV)
          for (c <- 0 until C){
            for (j <- 0 until sN){
              for (k <- 0 until sT) {
                for (l <- 0 until sV){
                  var temp: Double = 0
                  // need to update in term of dimension array
                  temp = normpdf(sX(j)(k)(l), mu(k)(l)(c), sqrt(s2(l)(c)))
                  //if (temp > 1){
                  //  print(temp)
                  //  print(" ")
                  //  print(sX(j)(k)(l), mu(k)(l)(c), s2(l)(c))
                  //  println()
                  //}
                  if (temp < normpdf(3)){
                    temp = normpdf(3)
                  }
                  distr_c(j)(k)(l) = temp
                }
              }
            }

            //reshape distribution vector
            var temp1: Array[Array[Double]] = Array.ofDim(sN, sV*sT)
            for (l <- 0 until sN){
              for (k <- 0 until sV){
                for (j <- 0 until sT){
                  //println(temp1(0).length, k*sT+j)
                  temp1(l)(k*sT+j) = distr_c(l)(j)(k)
                }
              }
            }
            //println(temp1.deep.mkString("\n"))
            //distr_c.indices.map(i => distr_c(i).indices.map(j =>  distr_c.indices.map(k => temp1(i)(k*sT+j) = distr_c(i)(j)(k))))
            // product of distribution function
            var prod_distrc_c : Array[Double] = Array.fill(sN)(1)
            for (j <- 0 until sN){
              for (k <- 0 until sV*sT){
                prod_distrc_c(j) *= temp1(j)(k)
              }
            }
            for (j <- 0 until sN){
              Q(j)(c) = prod_distrc_c(j) * theta(c)
            }
            //end
          }
//          println()
//          println(distr_c.deep.mkString("\n"))
//          println()
          val sumtQ = Array.fill(C)(Q.map(_.sum)).transpose
          Q.indices.map(i =>  Q(i).indices.map(j => Q(i)(j)/= sumtQ(i)(j)))
          //println(Q.deep.mkString("\n"))
          //end
        }
        // update mu, s2 and theta
        for (c <- 0 until C){
          val sumQ = Q.map(_(c)).sum

          theta(c) = sumQ/sN
          //println(theta.deep.mkString(" "))
          for (v <- 0 until sV ){
            val var2 = sT * sumQ
            val temp_var2 = (DenseMatrix(sX.map(_.map(_(v))):_*) - DenseMatrix(Array.fill(sN)(mu.map(_.map(_(c))).map(_(v))):_*)).map(x => x*x)
            val var1 = DenseVector(Q.map(_(c)):_*).t dot sum(temp_var2, Axis._1).t
            //println((DenseMatrix(sX.map(_.map(_(v))):_*)))
            s2(v)(c) = (n0*s2_0(v) + var1) / (n0 + var2)

            val A =  DenseMatrix(invS_0(v):_*) + (sumQ/s2(v)(c))* DenseMatrix.eye[Double](sT)
            val b =  DenseMatrix(invS_0(v):_*) * DenseMatrix(mu_0.map(_(v)):_*) + DenseMatrix(sX.map(_.map(_(v))):_*).t * DenseMatrix(Q.map(_(c)):_*) / s2(v)(c)
            val temp_r : Array[Double] = (A \ b).toArray
//            println(temp_r.deep.mkString(" "))
//            println()
            for (t <- 0 until sT){
              mu(t)(v)(c) = temp_r(t)
            }
            //mu.map(_.map(_(c))).map(_(v)).indices.map(x => mu(x)(v)(c) = temp_r(x))
          }
        }
//        println()
//        println(s2.deep.mkString("\n"))
//        println()
      } // end for i=1:I

      // compute assignments for all data
      Q  = GMMposterior.GMM_posterior(x, C, mu, s2, theta, dim_idx, time_idx, missing)
      // DONE FIXBUGS SINCE THERE

    } else {
      print("The value of the variable missing is not 0 or 1")
    }
    (Q , mu, s2, theta, dim_idx, time_idx)
  }

}

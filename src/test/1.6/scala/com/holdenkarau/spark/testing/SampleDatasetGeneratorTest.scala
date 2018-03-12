package com.holdenkarau.spark.testing

import org.apache.spark.sql.{Dataset, SQLContext}
import org.scalacheck.{Gen, Arbitrary}
import org.scalacheck.Prop.forAll
import org.scalatest.FunSuite
import org.scalatest.prop.Checkers

class SampleDatasetGeneratorTest extends FunSuite
    with SharedSparkContext with Checkers {

  test("test generating Datasets[String]") {
    val sqlContext = new SQLContext(sc)
    import sqlContext.implicits._

    val property =
      forAll(
        DatasetGenerator.genDataset[String](sqlContext)(
          Arbitrary.arbitrary[String])) {
        dataset => dataset.map(_.length).count() == dataset.count()
      }

    check(property)
  }

  test("test generating sized Datasets[String]") {
    val sqlContext = new SQLContext(sc)
    import sqlContext.implicits._

    val property =
      forAll {
        DatasetGenerator.genSizedDataset[(Int, String)](sqlContext) { size =>
          Gen.listOfN(size, Arbitrary.arbitrary[Char]).map(l => (size, l.mkString))
        }
      }{
        dataset => dataset.collect().forall(e => e._1 == e._2.length) &&
          dataset.map(_._2.length).count() == dataset.count()
      }

    check(property)
  }

  test("test generating Datasets[Custom Class]") {
    val sqlContext = new SQLContext(sc)
    import sqlContext.implicits._

    val carGen: Gen[Dataset[Car]] =
      DatasetGenerator.genDataset[Car](sqlContext) {
        val generator: Gen[Car] = for {
          name <- Arbitrary.arbitrary[String]
          speed <- Arbitrary.arbitrary[Int]
        } yield (Car(name, speed))

        generator
    }

    val property =
      forAll(carGen) {
        dataset => dataset.map(_.speed).count() == dataset.count()
      }

    check(property)
  }

  test("test generating sized Datasets[Custom Class]") {
    val sqlContext = new SQLContext(sc)
    import sqlContext.implicits._

    val carGen: Gen[Dataset[List[Car]]] =
      DatasetGenerator.genSizedDataset[List[Car]](sqlContext) { size =>
        val slowCarsTopNumber = math.ceil(size * 0.1).toInt
        def generator(speed: Gen[Int]): Gen[Car] = for {
          name <- Arbitrary.arbitrary[String]
          speed <- speed
        } yield (Car(name, speed))

        for {
          slowCarsNumber <- Gen.choose(0, slowCarsTopNumber)
          slowCars <- Gen.listOfN(slowCarsNumber, generator(Gen.choose(0, 20)))
          normalSpeedCars <- Gen.listOfN(size - slowCarsNumber, generator(Gen.choose(21, 150)))
        } yield {
          slowCars ++ normalSpeedCars
        }
      }

    val property =
      forAll(carGen.map(_.flatMap(identity))) {
        dataset =>
          val cars = dataset.collect()
          val dataSetSize  = cars.length
          val slowCars = cars.filter(_.speed < 21)
          slowCars.length <= dataSetSize * 0.1 && cars.map(_.speed).length == dataSetSize
      }

    check(property)
  }
}

case class Car(name: String, speed: Int)

// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "ChaveiroCar",
    platforms: [.iOS(.v17)],
    products: [
        .library(name: "ChaveiroCarKit", targets: ["ChaveiroCarKit"]),
    ],
    targets: [
        .target(name: "ChaveiroCarKit"),
    ]
)

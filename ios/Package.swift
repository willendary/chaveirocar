// swift-tools-version: 5.10
import PackageDescription

let package = Package(
    name: "ChaveiroCar",
    defaultLocalization: "pt",
    platforms: [
        .iOS(.v16),
        .macOS(.v13),
    ],
    products: [
        .library(
            name: "ChaveiroCarCore",
            targets: ["ChaveiroCarCore"]
        ),
        .executable(
            name: "ChaveiroCarApp",
            targets: ["ChaveiroCarApp"]
        ),
    ],
    targets: [
        .target(
            name: "ChaveiroCarCore",
            path: "Sources/ChaveiroCarCore"
        ),
        .executableTarget(
            name: "ChaveiroCarApp",
            dependencies: ["ChaveiroCarCore"],
            path: "Sources/ChaveiroCarApp"
        ),
        .testTarget(
            name: "ChaveiroCarTests",
            dependencies: ["ChaveiroCarCore"],
            path: "Tests/ChaveiroCarTests"
        ),
    ]
)

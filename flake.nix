{
  description = "Development shell for lokksmith";

  # Stable release. Bump to the next one (nixos-YY.MM) every six months.
  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-26.05";

  outputs =
    { nixpkgs, ... }:
    let
      systems = [
        "aarch64-darwin"
        "x86_64-darwin"
        "aarch64-linux"
        "x86_64-linux"
      ];
      forAllSystems = f: nixpkgs.lib.genAttrs systems (system: f nixpkgs.legacyPackages.${system});
    in
    {
      # Node.js LTS with npm, like CI (lts/*). Loaded by direnv (.envrc) or `nix develop`.
      # mkShellNoCC because mkShell's darwin stdenv sets DEVELOPER_DIR/SDKROOT to a macOS-only
      # Nix SDK and puts xcbuild's xcrun on PATH, which hides Xcode's iOS SDKs from Kotlin/Native.
      devShells = forAllSystems (pkgs: {
        default = pkgs.mkShellNoCC {
          packages = [ pkgs.nodejs ];
        };
      });
    };
}

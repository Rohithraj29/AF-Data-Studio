
import type { NextConfig } from "next";

const backendUrl = process.env.BACKEND_API_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  cacheComponents: true,
  partialPrefetching: true,

  async rewrites() {
    return [
      {
        source: "/backend/:path*",
        destination: `${backendUrl}/api/:path*`,
      },
    ];
  },

  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
};

export default nextConfig;
